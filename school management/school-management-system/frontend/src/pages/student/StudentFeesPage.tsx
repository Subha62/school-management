import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { FeeStatusChip } from '../../features/fees/components/FeeStatusChip';
import { FeeStatusFilter } from '../../features/fees/components/FeeStatusFilter';
import { useMyFees } from '../../features/fees/hooks';
import { formatMoney } from '../../features/fees/labels';
import type { FeeStatus, StudentFee } from '../../types/fees';
import { formatDate } from '../../utils/format';

export function StudentFeesPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<FeeStatus | undefined>(undefined);
  const fees = useMyFees({ page, size, status });

  const columns: Column<StudentFee>[] = [
    {
      id: 'fee',
      header: 'Fee',
      render: (fee) => (
        <Link component={RouterLink} to={`/student/fees/${fee.id}`} underline="hover">
          {fee.description ? `${fee.categoryName} · ${fee.description}` : fee.categoryName}
        </Link>
      ),
    },
    { id: 'year', header: 'Academic year', hideBelow: 'md', render: (fee) => fee.academicYearName },
    { id: 'due', header: 'Due date', render: (fee) => formatDate(fee.dueDate) },
    { id: 'amount', header: 'Amount', hideBelow: 'lg', render: (fee) => formatMoney(fee.amount) },
    { id: 'paid', header: 'Paid', hideBelow: 'lg', render: (fee) => formatMoney(fee.paidAmount) },
    {
      id: 'balance',
      header: 'Balance',
      render: (fee) => (
        <Typography
          component="span"
          variant="body2"
          sx={{
            fontWeight: 700,
            color: fee.status === 'OVERDUE' ? 'warning.dark' : 'text.primary',
          }}
        >
          {formatMoney(fee.balance)}
        </Typography>
      ),
    },
    { id: 'status', header: 'Status', render: (fee) => <FeeStatusChip status={fee.status} /> },
  ];
  const actions: RowAction<StudentFee>[] = [
    { label: 'Open', onSelect: (fee) => void navigate(`/student/fees/${fee.id}`) },
  ];

  return (
    <>
      <PageHeader
        title="Fees"
        subtitle="What you owe the school, what has been paid, and your receipts."
      />
      <Stack sx={{ mb: 2 }}>
        <FeeStatusFilter
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
      </Stack>
      <DataList
        data={fees.data}
        columns={columns}
        actions={actions}
        getRowId={(fee) => fee.id}
        getRowLabel={(fee) => fee.categoryName}
        isLoading={fees.isPending}
        error={fees.error}
        onRetry={() => void fees.refetch()}
        emptyTitle="No fees found"
        emptyHint={
          status ? 'No fee has this status.' : 'Fees the school bills you for appear here.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
    </>
  );
}
