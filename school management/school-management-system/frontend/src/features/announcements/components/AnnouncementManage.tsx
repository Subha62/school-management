import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { useNotify } from '../../../app/notifications';
import { ConfirmDialog } from '../../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../../components/DataList';
import { SearchField } from '../../../components/SearchField';
import { StatusChip } from '../../../components/StatusChip';
import type {
  Announcement,
  AnnouncementAction,
  AnnouncementManager,
  AnnouncementStatus,
} from '../../../types/announcements';
import { formatDateTime } from '../../assignments/labels';
import { formatDateRange } from '../../exams/labels';
import { useAnnouncementAction, useDeleteAnnouncement, useManagedAnnouncements } from '../hooks';
import {
  ANNOUNCEMENT_ACTIONS,
  STATUS_LABELS,
  STATUS_ORDER,
  audienceText,
  displayState,
  stateTone,
} from '../labels';
import { AnnouncementFormDialog } from './AnnouncementFormDialog';

const ALL = 'all';

/** Writing, publishing and archiving announcements. Teachers only see their own. */
export function AnnouncementManage({ manager }: { manager: AnnouncementManager }) {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(ALL);
  const [editing, setEditing] = useState<Announcement | 'new' | null>(null);
  const [confirming, setConfirming] = useState<{
    announcement: Announcement;
    action: AnnouncementAction;
  } | null>(null);
  const [deleting, setDeleting] = useState<Announcement | null>(null);

  const list = useManagedAnnouncements(manager, {
    page,
    size,
    search: search || undefined,
    status: status === ALL ? undefined : (status as AnnouncementStatus),
  });
  const transition = useAnnouncementAction(manager);
  const remove = useDeleteAnnouncement(manager);

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);
  const filtered = search !== '' || status !== ALL;

  const columns: Column<Announcement>[] = [
    { id: 'title', header: 'Title', render: (a) => a.title },
    { id: 'audience', header: 'Audience', render: (a) => audienceText(a) },
    {
      id: 'state',
      header: 'State',
      render: (a) => {
        const state = displayState(a);
        return <StatusChip label={state} tone={stateTone(state)} />;
      },
    },
    {
      id: 'window',
      header: 'Visible',
      hideBelow: 'lg',
      render: (a) =>
        a.publishAt || a.expiresAt
          ? formatDateRange(a.publishAt, a.expiresAt, formatDateTime)
          : a.publishedAt
            ? `Since ${formatDateTime(a.publishedAt)}`
            : 'Not published',
    },
    {
      id: 'reads',
      header: 'Read by',
      hideBelow: 'md',
      render: (a) => (a.status === 'DRAFT' ? '-' : (a.readCount ?? 0)),
    },
  ];
  const actions: RowAction<Announcement>[] = [
    { label: 'Edit', onSelect: setEditing, hidden: (a) => a.status === 'ARCHIVED' },
    {
      label: 'Publish',
      onSelect: (a) => setConfirming({ announcement: a, action: 'publish' }),
      hidden: (a) => a.status !== 'DRAFT',
    },
    {
      label: 'Archive',
      onSelect: (a) => setConfirming({ announcement: a, action: 'archive' }),
      hidden: (a) => a.status !== 'PUBLISHED',
    },
    {
      label: 'Delete',
      onSelect: setDeleting,
      destructive: true,
      hidden: (a) => a.status !== 'DRAFT',
    },
  ];

  const cancelConfirm = () => {
    transition.reset();
    setConfirming(null);
  };
  const cancelDelete = () => {
    remove.reset();
    setDeleting(null);
  };
  const copy = confirming ? ANNOUNCEMENT_ACTIONS[confirming.action] : null;

  return (
    <>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap', alignItems: { sm: 'center' } }}
      >
        <SearchField label="Search by title" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 170 }}
        >
          <MenuItem value={ALL}>All statuses</MenuItem>
          {STATUS_ORDER.map((value) => (
            <MenuItem key={value} value={value}>
              {STATUS_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setEditing('new')}
          sx={{ ml: { sm: 'auto' } }}
        >
          New announcement
        </Button>
      </Stack>

      <DataList
        data={list.data}
        columns={columns}
        actions={actions}
        getRowId={(a) => a.id}
        getRowLabel={(a) => a.title}
        isLoading={list.isPending}
        error={list.error}
        onRetry={() => void list.refetch()}
        emptyTitle="No announcements found"
        emptyHint={filtered ? 'Try a different search or filter.' : 'Write the first announcement.'}
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <AnnouncementFormDialog
          manager={manager}
          announcement={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={confirming !== null}
        title={copy?.title ?? ''}
        message={`${confirming?.announcement.title ?? ''}: ${copy?.message ?? ''}`}
        confirmLabel={copy?.label ?? 'Confirm'}
        destructive={copy?.destructive}
        loading={transition.isPending}
        error={transition.error}
        onCancel={cancelConfirm}
        onConfirm={() => {
          if (!confirming) return;
          transition.mutate(
            { id: confirming.announcement.id, action: confirming.action },
            {
              onSuccess: () => {
                notify.success(ANNOUNCEMENT_ACTIONS[confirming.action].done);
                setConfirming(null);
              },
            },
          );
        }}
      />
      <ConfirmDialog
        open={deleting !== null}
        title="Delete draft?"
        message={`${deleting?.title ?? 'This draft'} is deleted permanently. Only drafts can be deleted.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Draft deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
