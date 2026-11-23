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
import { StatusChip } from '../../components/StatusChip';
import { AcademicYearFormDialog } from '../../features/academics/components/AcademicYearFormDialog';
import {
  useAcademicYears,
  useActivateAcademicYear,
  useCloseAcademicYear,
  useDeleteAcademicYear,
} from '../../features/academics/hooks';
import type { AcademicYear, AcademicYearStatus } from '../../types/academics';
import { formatDate } from '../../utils/format';

type Pending = { year: AcademicYear; kind: 'activate' | 'close' | 'delete' };

const STATUS_TONE = { PLANNED: 'info', ACTIVE: 'success', CLOSED: 'default' } as const;
const STATUS_LABEL = { PLANNED: 'Planned', ACTIVE: 'Active', CLOSED: 'Closed' } as const;

export function AcademicYearsPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<AcademicYearStatus | ''>('');
  const [editing, setEditing] = useState<AcademicYear | 'new' | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);

  const years = useAcademicYears({
    page,
    size,
    search: search || undefined,
    status: status || undefined,
  });
  const activate = useActivateAcademicYear();
  const close = useCloseAcademicYear();
  const remove = useDeleteAcademicYear();
  const mutation = { activate, close, delete: remove }[pending?.kind ?? 'activate'];

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<AcademicYear>[] = [
    { id: 'name', header: 'Name', render: (year) => year.name },
    {
      id: 'dates',
      header: 'Dates',
      render: (year) => `${formatDate(year.startDate)} - ${formatDate(year.endDate)}`,
    },
    {
      id: 'status',
      header: 'Status',
      render: (year) => (
        <StatusChip label={STATUS_LABEL[year.status]} tone={STATUS_TONE[year.status]} />
      ),
    },
  ];

  const actions: RowAction<AcademicYear>[] = [
    { label: 'Edit', onSelect: setEditing, hidden: (year) => year.status === 'CLOSED' },
    {
      label: 'Activate',
      onSelect: (year) => setPending({ year, kind: 'activate' }),
      hidden: (year) => year.status !== 'PLANNED',
    },
    {
      label: 'Close year',
      onSelect: (year) => setPending({ year, kind: 'close' }),
      hidden: (year) => year.status !== 'ACTIVE',
      destructive: true,
    },
    {
      label: 'Delete',
      onSelect: (year) => setPending({ year, kind: 'delete' }),
      hidden: (year) => year.status !== 'PLANNED',
      destructive: true,
    },
  ];

  const copy = {
    activate: {
      title: 'Activate academic year?',
      confirmLabel: 'Activate',
      success: 'Academic year activated',
      message: (name: string) =>
        `${name} becomes the current academic year. Only one year can be active at a time.`,
    },
    close: {
      title: 'Close academic year?',
      confirmLabel: 'Close year',
      success: 'Academic year closed',
      message: (name: string) =>
        `${name} will be closed. This cannot be undone and its sections and assignments become read-only.`,
    },
    delete: {
      title: 'Delete academic year?',
      confirmLabel: 'Delete',
      success: 'Academic year deleted',
      message: (name: string) => `${name} will be deleted permanently.`,
    },
  } as const;

  const confirm = () => {
    if (!pending) return;
    mutation.mutate(pending.year.id, {
      onSuccess: () => {
        notify.success(copy[pending.kind].success);
        setPending(null);
      },
    });
  };

  const cancel = () => {
    mutation.reset();
    setPending(null);
  };

  return (
    <>
      <PageHeader
        title="Academic years"
        subtitle="Plan, activate and close the school's academic years."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New academic year
          </Button>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <SearchField label="Search by name" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as AcademicYearStatus | '');
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          <MenuItem value="PLANNED">Planned</MenuItem>
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="CLOSED">Closed</MenuItem>
        </TextField>
      </Stack>

      <DataList
        data={years.data}
        columns={columns}
        actions={actions}
        getRowId={(year) => year.id}
        getRowLabel={(year) => year.name}
        isLoading={years.isPending}
        error={years.error}
        onRetry={() => void years.refetch()}
        emptyTitle="No academic years found"
        emptyHint={
          search || status
            ? 'Try a different search or filter.'
            : 'Create the first academic year to get started.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <AcademicYearFormDialog
          year={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={pending !== null}
        title={pending ? copy[pending.kind].title : ''}
        message={pending ? copy[pending.kind].message(pending.year.name) : ''}
        confirmLabel={pending ? copy[pending.kind].confirmLabel : ''}
        destructive={pending?.kind !== 'activate'}
        loading={mutation.isPending}
        error={mutation.error}
        onConfirm={confirm}
        onCancel={cancel}
      />
    </>
  );
}
