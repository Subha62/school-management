import Box from '@mui/material/Box';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { ErrorState } from '../../components/ErrorState';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { StatusChip } from '../../components/StatusChip';
import { ReasonDialog } from '../../features/fees/components/ReasonDialog';
import { useCollections, usePayments, useReversePayment } from '../../features/fees/hooks';
import {
  PAYMENT_METHOD_LABELS,
  PAYMENT_METHOD_ORDER,
  formatMoney,
} from '../../features/fees/labels';
import type { Collections, Payment, PaymentMethod } from '../../types/fees';
import { formatDate } from '../../utils/format';

const ALL = 'all';
type ReversedFilter = typeof ALL | 'no' | 'yes';

function CollectionTotals({ totals }: { totals: Collections }) {
  const cards = [
    { label: 'Payments', value: String(totals.payments) },
    { label: 'Received', value: formatMoney(totals.received) },
    { label: 'Reversed', value: formatMoney(totals.reversed) },
    { label: 'Net collected', value: formatMoney(totals.net) },
  ];
  return (
    <Box sx={{ mb: 2 }}>
      <Box
        component="dl"
        aria-label="Collections"
        sx={{
          m: 0,
          display: 'grid',
          gap: 1.5,
          gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' },
        }}
      >
        {cards.map((card) => (
          <Paper key={card.label} variant="outlined" sx={{ p: 2, minWidth: 0 }}>
            <Typography component="dt" variant="body2" color="text.secondary">
              {card.label}
            </Typography>
            <Typography component="dd" variant="h6" sx={{ m: 0, overflowWrap: 'anywhere' }}>
              {card.value}
            </Typography>
          </Paper>
        ))}
      </Box>
      {totals.byMethod.length > 0 ? (
        <Stack
          component="ul"
          aria-label="Received by method"
          direction="row"
          spacing={1}
          useFlexGap
          sx={{ flexWrap: 'wrap', listStyle: 'none', m: 0, mt: 1.5, p: 0 }}
        >
          {totals.byMethod.map((method) => (
            <li key={method.method}>
              <StatusChip
                label={`${PAYMENT_METHOD_LABELS[method.method]}: ${formatMoney(method.total)} (${method.count})`}
              />
            </li>
          ))}
        </Stack>
      ) : null}
    </Box>
  );
}

export function PaymentsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [method, setMethod] = useState(ALL);
  const [reversedFilter, setReversedFilter] = useState<ReversedFilter>(ALL);
  const [reversing, setReversing] = useState<Payment | null>(null);

  const rangeInvalid = from !== '' && to !== '' && to < from;
  const fromDate = rangeInvalid ? undefined : from || undefined;
  const toDate = rangeInvalid ? undefined : to || undefined;

  const payments = usePayments({
    page,
    size,
    from: fromDate,
    to: toDate,
    method: method === ALL ? undefined : (method as PaymentMethod),
    reversed: reversedFilter === ALL ? undefined : reversedFilter === 'yes',
    search: search || undefined,
  });
  const collections = useCollections(fromDate, toDate);
  const reverse = useReversePayment();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);
  const filtered =
    search !== '' || from !== '' || to !== '' || method !== ALL || reversedFilter !== ALL;

  const columns: Column<Payment>[] = [
    { id: 'receipt', header: 'Receipt', render: (p) => p.receiptNumber },
    {
      id: 'student',
      header: 'Student',
      render: (p) => `${p.studentName} (${p.admissionNumber})`,
    },
    { id: 'category', header: 'Fee', hideBelow: 'md', render: (p) => p.categoryName },
    { id: 'date', header: 'Paid on', render: (p) => formatDate(p.paidOn) },
    {
      id: 'method',
      header: 'Method',
      hideBelow: 'lg',
      render: (p) => PAYMENT_METHOD_LABELS[p.method],
    },
    {
      id: 'amount',
      header: 'Amount',
      render: (p) => (
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', flexWrap: 'wrap' }}
          useFlexGap
        >
          <Typography
            component="span"
            variant="body2"
            sx={{ fontWeight: 600, textDecoration: p.reversed ? 'line-through' : 'none' }}
          >
            {formatMoney(p.amount)}
          </Typography>
          {p.reversed ? <StatusChip label="Reversed" tone="warning" /> : null}
        </Stack>
      ),
    },
  ];
  const actions: RowAction<Payment>[] = [
    { label: 'Receipt', onSelect: (p) => void navigate(`/admin/payments/${p.id}/receipt`) },
    {
      label: 'Reverse',
      onSelect: setReversing,
      destructive: true,
      hidden: (p) => p.reversed,
    },
  ];

  return (
    <>
      <PageHeader
        title="Payments"
        subtitle="Every payment received, with its receipt. Mistakes are reversed, never deleted."
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap' }}
      >
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
        <TextField
          select
          size="small"
          label="Method"
          value={method}
          onChange={(event) => {
            setMethod(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All methods</MenuItem>
          {PAYMENT_METHOD_ORDER.map((value) => (
            <MenuItem key={value} value={value}>
              {PAYMENT_METHOD_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Reversal"
          value={reversedFilter}
          onChange={(event) => {
            setReversedFilter(event.target.value as ReversedFilter);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All payments</MenuItem>
          <MenuItem value="no">Not reversed</MenuItem>
          <MenuItem value="yes">Reversed only</MenuItem>
        </TextField>
        <SearchField label="Search receipt or student" onSearch={onSearch} />
      </Stack>

      {collections.data ? <CollectionTotals totals={collections.data} /> : null}
      {collections.error ? (
        <Stack sx={{ mb: 2 }}>
          <ErrorState
            title="Unable to load the totals"
            error={collections.error}
            onRetry={() => void collections.refetch()}
          />
        </Stack>
      ) : null}

      <DataList
        data={payments.data}
        columns={columns}
        actions={actions}
        getRowId={(p) => p.id}
        getRowLabel={(p) => p.receiptNumber}
        isLoading={payments.isPending}
        error={payments.error}
        onRetry={() => void payments.refetch()}
        emptyTitle="No payments found"
        emptyHint={
          filtered ? 'Try a different search or filter.' : 'Payments appear here once recorded.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {reversing ? (
        <ReasonDialog
          title="Reverse payment"
          description={`Receipt ${reversing.receiptNumber} for ${formatMoney(reversing.amount)} from ${reversing.studentName} is cancelled and the amount becomes payable again. The receipt stays on record, marked as reversed.`}
          submitLabel="Reverse payment"
          successMessage="Payment reversed"
          onSubmit={(reason) => reverse.mutateAsync({ id: reversing.id, reason })}
          onClose={() => setReversing(null)}
        />
      ) : null}
    </>
  );
}
