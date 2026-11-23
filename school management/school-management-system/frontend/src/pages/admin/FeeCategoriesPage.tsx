import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import { useCallback, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ActiveChip } from '../../components/StatusChip';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { CategoryFormDialog } from '../../features/fees/components/CategoryFormDialog';
import { useCategories, useDeleteCategory } from '../../features/fees/hooks';
import type { FeeCategory } from '../../types/fees';

export function FeeCategoriesPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState<FeeCategory | 'new' | null>(null);
  const [deleting, setDeleting] = useState<FeeCategory | null>(null);
  const categories = useCategories({ page, size, search: search || undefined });
  const remove = useDeleteCategory();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<FeeCategory>[] = [
    { id: 'name', header: 'Name', render: (category) => category.name },
    {
      id: 'description',
      header: 'Description',
      hideBelow: 'md',
      render: (category) => category.description ?? '-',
    },
    {
      id: 'active',
      header: 'Status',
      render: (category) => <ActiveChip active={category.active} />,
    },
  ];
  const actions: RowAction<FeeCategory>[] = [
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
        title="Fee categories"
        subtitle="The kinds of fee the school charges, such as tuition, transport or exam fees."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New category
          </Button>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <SearchField label="Search by name" onSearch={onSearch} />
      </Stack>
      <DataList
        data={categories.data}
        columns={columns}
        actions={actions}
        getRowId={(category) => category.id}
        getRowLabel={(category) => category.name}
        isLoading={categories.isPending}
        error={categories.error}
        onRetry={() => void categories.refetch()}
        emptyTitle="No fee categories found"
        emptyHint={search ? 'Try a different search.' : 'Create the first fee category.'}
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <CategoryFormDialog
          category={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete fee category?"
        message={`${deleting?.name ?? 'This category'} is deleted permanently. A category that is used by a fee structure cannot be deleted; deactivate it instead.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Fee category deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
