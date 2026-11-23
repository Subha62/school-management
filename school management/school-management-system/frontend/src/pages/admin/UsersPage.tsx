import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { useAuth } from '../../auth/authContext';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { StatusChip } from '../../components/StatusChip';
import { AdminFormDialog } from '../../features/people/components/AdminFormDialog';
import { useChangeUserStatus, useUnlockUser, useUsers } from '../../features/people/hooks';
import {
  ACCOUNT_STATUS_LABELS,
  ROLE_LABELS,
  fullName,
  toOptions,
} from '../../features/people/labels';
import type { AccountStatus, AdminUser, RoleName } from '../../types/people';

const ALL = 'all';

const tone = (status: AccountStatus) =>
  status === 'ACTIVE'
    ? 'success'
    : status === 'SUSPENDED' || status === 'LOCKED'
      ? 'warning'
      : status === 'PENDING'
        ? 'info'
        : 'default';

interface StatusChange {
  user: AdminUser;
  status: AccountStatus;
}

const VERBS: Partial<Record<AccountStatus, { title: string; confirm: string; text: string }>> = {
  SUSPENDED: {
    title: 'Suspend account?',
    confirm: 'Suspend',
    text: 'cannot sign in while suspended and is signed out of every device now. You can activate the account again later.',
  },
  ARCHIVED: {
    title: 'Archive account?',
    confirm: 'Archive',
    text: 'is signed out of every device now and cannot sign in. Archived accounts keep their records and can be activated again.',
  },
  ACTIVE: {
    title: 'Activate account?',
    confirm: 'Activate',
    text: 'will be able to sign in again.',
  },
};

export function UsersPage() {
  const notify = useNotify();
  const { user: me } = useAuth();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [creating, setCreating] = useState(false);
  const [change, setChange] = useState<StatusChange | null>(null);

  const users = useUsers({
    page,
    size,
    search: search || undefined,
    role: role === ALL ? undefined : (role as RoleName),
    status: status === ALL ? undefined : (status as AccountStatus),
  });
  const changeStatus = useChangeUserStatus();
  const unlock = useUnlockUser();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<AdminUser>[] = [
    { id: 'name', header: 'Name', render: (u) => fullName(u) },
    { id: 'username', header: 'Sign-in ID', render: (u) => u.username },
    { id: 'email', header: 'Email', hideBelow: 'lg', render: (u) => u.email },
    {
      id: 'role',
      header: 'Role',
      render: (u) => u.roles.map((name) => ROLE_LABELS[name]).join(', '),
    },
    {
      id: 'status',
      header: 'Status',
      render: (u) => (
        <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
          <StatusChip label={ACCOUNT_STATUS_LABELS[u.accountStatus]} tone={tone(u.accountStatus)} />
          {u.temporarilyLocked ? <StatusChip label="Locked out" tone="warning" /> : null}
        </Stack>
      ),
    },
  ];

  const isSelf = (user: AdminUser) => user.id === me?.id;
  const actions: RowAction<AdminUser>[] = [
    {
      label: 'Suspend',
      onSelect: (user) => setChange({ user, status: 'SUSPENDED' }),
      hidden: (user) => isSelf(user) || user.accountStatus !== 'ACTIVE',
    },
    {
      label: 'Archive',
      onSelect: (user) => setChange({ user, status: 'ARCHIVED' }),
      destructive: true,
      hidden: (user) =>
        isSelf(user) || user.accountStatus === 'ARCHIVED' || user.accountStatus === 'PENDING',
    },
    {
      label: 'Activate',
      onSelect: (user) => setChange({ user, status: 'ACTIVE' }),
      hidden: (user) =>
        isSelf(user) || (user.accountStatus !== 'SUSPENDED' && user.accountStatus !== 'ARCHIVED'),
    },
    {
      label: 'Clear lockout',
      onSelect: (user) =>
        unlock.mutate(user.id, {
          onSuccess: () => notify.success('Lockout cleared'),
          onError: (error) => notify.error(error.message),
        }),
      hidden: (user) => !user.temporarilyLocked,
    },
  ];

  const verb = change ? VERBS[change.status] : undefined;

  return (
    <>
      <PageHeader
        title="Users"
        subtitle="Every account in the school. Students and teachers are created from their own pages."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            New administrator
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by name, ID or email" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Role"
          value={role}
          onChange={(event) => {
            setRole(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All roles</MenuItem>
          {toOptions<RoleName>(ROLE_LABELS).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All statuses</MenuItem>
          {toOptions<AccountStatus>(ACCOUNT_STATUS_LABELS).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <DataList
        data={users.data}
        columns={columns}
        actions={actions}
        getRowId={(user) => user.id}
        getRowLabel={(user) => fullName(user)}
        isLoading={users.isPending}
        error={users.error}
        onRetry={() => void users.refetch()}
        emptyTitle="No users found"
        emptyHint="Change the filters or search text."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {creating ? <AdminFormDialog onClose={() => setCreating(false)} /> : null}
      <ConfirmDialog
        open={change !== null}
        title={verb?.title ?? ''}
        message={change && verb ? `${fullName(change.user)} ${verb.text}` : ''}
        confirmLabel={verb?.confirm ?? 'Confirm'}
        destructive={change?.status === 'ARCHIVED'}
        loading={changeStatus.isPending}
        error={changeStatus.error}
        onCancel={() => {
          changeStatus.reset();
          setChange(null);
        }}
        onConfirm={() => {
          if (!change) return;
          changeStatus.mutate(
            { id: change.user.id, status: change.status, version: change.user.version },
            {
              onSuccess: () => {
                notify.success('Account updated');
                setChange(null);
              },
            },
          );
        }}
      />
    </>
  );
}
