import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { ActiveChip } from '../../components/StatusChip';
import { GradeFormDialog } from '../../features/academics/components/GradeFormDialog';
import { useDeleteGrade, useGrades } from '../../features/academics/hooks';
import type { GradeLevel } from '../../types/academics';

type ActiveFilter = '' | 'true' | 'false';

export function GradesPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState<ActiveFilter>('');
  const [editing, setEditing] = useState<GradeLevel | 'new' | null>(null);
  const [deleting, setDeleting] = useState<GradeLevel | null>(null);

  const grades = useGrades({
    page,
    size,
    search: search || undefined,
    active: active === '' ? undefined : active === 'true',
  });
  const remove = useDeleteGrade();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<GradeLevel>[] = [
    { id: 'name', header: 'Name', render: (grade) => grade.name },
    { id: 'order', header: 'Display order', render: (grade) => grade.displayOrder },
    { id: 'status', header: 'Status', render: (grade) => <ActiveChip active={grade.active} /> },
  ];

  const actions: RowAction<GradeLevel>[] = [
    { label: 'Edit', onSelect: setEditing },
    { label: 'Delete', onSelect: setDeleting, destructive: true },
  ];

  const cancelDelete = () => {
    remove.reset();
    setDeleting(null);
  };

  return (
    <>
      <PageHeader
        title="Grades"
        subtitle="Grade levels, such as Grade 8, in the order they are shown."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New grade
          </Button>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <SearchField label="Search by name" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Status"
          value={active}
          onChange={(event) => {
            setActive(event.target.value as ActiveFilter);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All</MenuItem>
          <MenuItem value="true">Active</MenuItem>
          <MenuItem value="false">Inactive</MenuItem>
        </TextField>
      </Stack>

      <DataList
        data={grades.data}
        columns={columns}
        actions={actions}
        getRowId={(grade) => grade.id}
        getRowLabel={(grade) => grade.name}
        isLoading={grades.isPending}
        error={grades.error}
        onRetry={() => void grades.refetch()}
        emptyTitle="No grades found"
        emptyHint={
          search || active
            ? 'Try a different search or filter.'
            : 'Add the grades your school teaches.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <GradeFormDialog
          grade={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete grade?"
        message={`${deleting?.name ?? 'This grade'} will be deleted permanently. A grade that sections use can only be marked inactive.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Grade deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
