import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState } from 'react';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { AuditDetailDialog } from '../../features/audit/components/AuditDetailDialog';
import { useAuditActions, useAuditEntityTypes, useAuditLogs } from '../../features/audit/hooks';
import { actionLabel, actorLabel, entityTypeLabel } from '../../features/audit/labels';
import type { AuditEntry } from '../../types/audit';
import { formatDateTime } from '../../utils/format';

const ALL = 'all';

export function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [action, setAction] = useState(ALL);
  const [entityType, setEntityType] = useState(ALL);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<AuditEntry | null>(null);

  const rangeInvalid = from !== '' && to !== '' && to < from;
  const fromDate = rangeInvalid ? undefined : from || undefined;
  const toDate = rangeInvalid ? undefined : to || undefined;

  const actions = useAuditActions();
  const entityTypes = useAuditEntityTypes();
  const logs = useAuditLogs({
    page,
    size,
    action: action === ALL ? undefined : action,
    entityType: entityType === ALL ? undefined : entityType,
    from: fromDate,
    to: toDate,
    search: search || undefined,
    sort: 'createdAt',
    direction: 'desc',
  });

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);
  const filtered =
    search !== '' || from !== '' || to !== '' || action !== ALL || entityType !== ALL;

  const columns: Column<AuditEntry>[] = [
    { id: 'time', header: 'Time', render: (e) => formatDateTime(e.createdAt) },
    { id: 'action', header: 'Action', render: (e) => actionLabel(e.action) },
    {
      id: 'summary',
      header: 'Summary',
      render: (e) => (
        <Typography component="span" variant="body2" sx={{ overflowWrap: 'anywhere' }}>
          {e.summary}
        </Typography>
      ),
    },
    { id: 'actor', header: 'Actor', hideBelow: 'md', render: (e) => actorLabel(e) },
    {
      id: 'entity',
      header: 'Entity',
      hideBelow: 'lg',
      render: (e) => entityTypeLabel(e.entityType),
    },
  ];
  const rowActions: RowAction<AuditEntry>[] = [{ label: 'Details', onSelect: setSelected }];

  return (
    <>
      <PageHeader
        title="Audit logs"
        subtitle="A permanent, read-only record of who changed what and when."
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap' }}
      >
        <TextField
          select
          size="small"
          label="Action"
          value={action}
          onChange={(event) => {
            setAction(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value={ALL}>All actions</MenuItem>
          {(actions.data ?? []).map((value) => (
            <MenuItem key={value} value={value}>
              {actionLabel(value)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Entity type"
          value={entityType}
          onChange={(event) => {
            setEntityType(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value={ALL}>All entity types</MenuItem>
          {(entityTypes.data ?? []).map((value) => (
            <MenuItem key={value} value={value}>
              {entityTypeLabel(value)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="From"
          type="date"
          size="small"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
            setPage(0);
          }}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="date"
          size="small"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
            setPage(0);
          }}
          error={rangeInvalid}
          helperText={rangeInvalid ? 'The end date cannot be before the start date' : undefined}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <SearchField label="Search summary" onSearch={onSearch} />
      </Stack>

      <DataList
        data={logs.data}
        columns={columns}
        actions={rowActions}
        getRowId={(e) => e.id}
        getRowLabel={(e) => e.summary}
        isLoading={logs.isPending}
        error={logs.error}
        onRetry={() => void logs.refetch()}
        emptyTitle="No audit entries found"
        emptyHint={
          filtered
            ? 'Try a different search or filter.'
            : 'Activity is recorded here as it happens.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {selected ? <AuditDetailDialog entry={selected} onClose={() => setSelected(null)} /> : null}
    </>
  );
}
