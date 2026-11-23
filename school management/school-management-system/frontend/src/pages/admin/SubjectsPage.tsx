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
import { SubjectFormDialog } from '../../features/academics/components/SubjectFormDialog';
import { useDeleteSubject, useSubjects } from '../../features/academics/hooks';
import type { Subject } from '../../types/academics';

type ActiveFilter = '' | 'true' | 'false';

export function SubjectsPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [active, setActive] = useState<ActiveFilter>('');
  const [editing, setEditing] = useState<Subject | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Subject | null>(null);

  const subjects = useSubjects({
    page,
    size,
    search: search || undefined,
    active: active === '' ? undefined : active === 'true',
  });
  const remove = useDeleteSubject();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Subject>[] = [
    { id: 'code', header: 'Code', render: (subject) => subject.code },
    { id: 'name', header: 'Name', render: (subject) => subject.name },
    {
      id: 'description',
      header: 'Description',
      hideBelow: 'lg',
      render: (subject) => subject.description ?? '-',
    },
    { id: 'status', header: 'Status', render: (subject) => <ActiveChip active={subject.active} /> },
  ];

  const actions: RowAction<Subject>[] = [
    { label: 'Edit', onSelect: setEditing },
    { label: 'Delete', onSelect: setDeleting, destructive: true },
  ];

  return (
    <>
      <PageHeader
        title="Subjects"
        subtitle="Subjects the school teaches. Codes are unique across the school."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New subject
          </Button>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <SearchField label="Search by code or name" onSearch={onSearch} />
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
        data={subjects.data}
        columns={columns}
        actions={actions}
        getRowId={(subject) => subject.id}
        getRowLabel={(subject) => subject.name}
        isLoading={subjects.isPending}
        error={subjects.error}
        onRetry={() => void subjects.refetch()}
        emptyTitle="No subjects found"
        emptyHint={
          search || active
            ? 'Try a different search or filter.'
            : 'Add the subjects your school teaches.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <SubjectFormDialog
          subject={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete subject?"
        message={`${deleting?.name ?? 'This subject'} will be deleted permanently. A subject that is assigned to sections can only be marked inactive.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={() => {
          remove.reset();
          setDeleting(null);
        }}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Subject deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
