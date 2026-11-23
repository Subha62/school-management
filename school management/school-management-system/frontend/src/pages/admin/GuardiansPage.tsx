import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import { useCallback, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { GuardianFormDialog } from '../../features/people/components/GuardianFormDialog';
import { useDeleteGuardian, useGuardians } from '../../features/people/hooks';
import { RELATIONSHIP_LABELS, fullName } from '../../features/people/labels';
import type { Guardian } from '../../types/people';

export function GuardiansPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState<Guardian | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Guardian | null>(null);

  const guardians = useGuardians({ page, size, search: search || undefined });
  const remove = useDeleteGuardian();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Guardian>[] = [
    { id: 'name', header: 'Name', render: (g) => fullName(g) },
    {
      id: 'relationship',
      header: 'Relationship',
      render: (g) => RELATIONSHIP_LABELS[g.relationship],
    },
    { id: 'phone', header: 'Phone', render: (g) => g.phoneNumber },
    { id: 'email', header: 'Email', hideBelow: 'lg', render: (g) => g.email ?? '-' },
    { id: 'students', header: 'Students', hideBelow: 'sm', render: (g) => g.studentCount },
  ];

  const actions: RowAction<Guardian>[] = [
    { label: 'Edit', onSelect: setEditing },
    { label: 'Delete', onSelect: setDeleting, destructive: true },
  ];

  return (
    <>
      <PageHeader
        title="Guardians"
        subtitle="Parents and guardians. Link them to students from the student's page."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New guardian
          </Button>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <SearchField label="Search by name, phone or email" onSearch={onSearch} />
      </Stack>

      <DataList
        data={guardians.data}
        columns={columns}
        actions={actions}
        getRowId={(guardian) => guardian.id}
        getRowLabel={(guardian) => fullName(guardian)}
        isLoading={guardians.isPending}
        error={guardians.error}
        onRetry={() => void guardians.refetch()}
        emptyTitle="No guardians found"
        emptyHint="Add a guardian, or change the search text."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <GuardianFormDialog
          guardian={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete guardian?"
        message={`${deleting ? fullName(deleting) : 'This guardian'} will be deleted permanently. Guardians linked to students must be unlinked first.`}
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
              notify.success('Guardian deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
