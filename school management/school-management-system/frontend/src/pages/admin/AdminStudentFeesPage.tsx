import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { ErrorState } from '../../components/ErrorState';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { useGradeOptions, useYearOptions } from '../../features/academics/hooks';
import { FeeActionDialog, type FeeAction } from '../../features/fees/components/FeeActionDialogs';
import { FeeStatusChip } from '../../features/fees/components/FeeStatusChip';
import { FeeStatusFilter } from '../../features/fees/components/FeeStatusFilter';
import { FeeSummaryCards } from '../../features/fees/components/FeeSummaryCards';
import { useFeeSummary, useStudentFees } from '../../features/fees/hooks';
import { canRecordPayment, formatMoney } from '../../features/fees/labels';
import type { FeeStatus, StudentFee } from '../../types/fees';
import { formatDate } from '../../utils/format';

const ALL = 'all';

export function AdminStudentFeesPage() {
  const navigate = useNavigate();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const [gradeId, setGradeId] = useState(ALL);
  const [status, setStatus] = useState<FeeStatus | undefined>(undefined);
  const [action, setAction] = useState<FeeAction | null>(null);

  // The active academic year is the default; the first year stands in when none is active.
  const yearList = years.data?.content ?? [];
  const yearId = yearChoice ?? yearList.find((y) => y.status === 'ACTIVE')?.id ?? yearList[0]?.id;
  const gradeLevelId = gradeId === ALL ? undefined : gradeId;

  const summary = useFeeSummary(yearId, gradeLevelId);
  const fees = useStudentFees(
    {
      page,
      size,
      sort: 'dueDate',
      direction: 'asc',
      academicYearId: yearId,
      gradeLevelId,
      status,
      search: search || undefined,
    },
    !years.isPending,
  );

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<StudentFee>[] = [
    {
      id: 'student',
      header: 'Student',
      render: (fee) => (
        <>
          <Link component={RouterLink} to={`/admin/student-fees/${fee.id}`} underline="hover">
            {fee.studentName}
          </Link>
          <Typography variant="body2" color="text.secondary" component="span" sx={{ ml: 1 }}>
            {fee.admissionNumber}
          </Typography>
        </>
      ),
    },
    {
      id: 'fee',
      header: 'Fee',
      render: (fee) =>
        fee.description ? `${fee.categoryName} · ${fee.description}` : fee.categoryName,
    },
    { id: 'class', header: 'Class', hideBelow: 'md', render: (fee) => fee.className ?? '-' },
    { id: 'due', header: 'Due date', hideBelow: 'lg', render: (fee) => formatDate(fee.dueDate) },
    { id: 'amount', header: 'Amount', hideBelow: 'lg', render: (fee) => formatMoney(fee.amount) },
    {
      id: 'balance',
      header: 'Balance',
      render: (fee) => (
        <Typography
          component="span"
          variant="body2"
          sx={{
            fontWeight: 600,
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
    { label: 'Open', onSelect: (fee) => void navigate(`/admin/student-fees/${fee.id}`) },
    {
      label: 'Record payment',
      onSelect: (fee) => setAction({ type: 'pay', fee }),
      hidden: (fee) => !canRecordPayment(fee.status),
    },
    {
      label: 'Waive',
      onSelect: (fee) => setAction({ type: 'waive', fee }),
      hidden: (fee) => fee.status === 'WAIVED' || fee.status === 'PAID',
    },
    {
      label: 'Remove waiver',
      onSelect: (fee) => setAction({ type: 'unwaive', fee }),
      hidden: (fee) => fee.status !== 'WAIVED',
    },
  ];

  const filtered = search !== '' || gradeId !== ALL || status !== undefined;

  return (
    <>
      <PageHeader
        title="Student fees"
        subtitle="What each student owes, what has been paid, and the balance."
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
          label="Academic year"
          value={yearId ?? ''}
          onChange={(event) => {
            setYearChoice(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 170 }}
        >
          {yearList.map((year) => (
            <MenuItem key={year.id} value={year.id}>
              {year.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Grade"
          value={gradeId}
          onChange={(event) => {
            setGradeId(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 170 }}
        >
          <MenuItem value={ALL}>All grades</MenuItem>
          {(grades.data?.content ?? []).map((grade) => (
            <MenuItem key={grade.id} value={grade.id}>
              {grade.name}
            </MenuItem>
          ))}
        </TextField>
        <SearchField label="Search student" onSearch={onSearch} />
      </Stack>

      {summary.data ? <FeeSummaryCards summary={summary.data} /> : null}
      {summary.error ? (
        <Stack sx={{ mb: 2 }}>
          <ErrorState
            title="Unable to load the fee summary"
            error={summary.error}
            onRetry={() => void summary.refetch()}
          />
        </Stack>
      ) : null}
      {!years.isPending && yearList.length === 0 ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Create an academic year before billing fees.
        </Alert>
      ) : null}

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
        getRowLabel={(fee) => `${fee.studentName} ${fee.categoryName}`}
        isLoading={fees.isPending}
        error={fees.error}
        onRetry={() => void fees.refetch()}
        emptyTitle="No student fees found"
        emptyHint={
          filtered
            ? 'Try a different search or filter.'
            : 'Generate fees from a fee structure to bill students.'
        }
        emptyAction={
          filtered ? undefined : (
            <Button component={RouterLink} to="/admin/fee-structures">
              Open fee structures
            </Button>
          )
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {action ? <FeeActionDialog action={action} onClose={() => setAction(null)} /> : null}
    </>
  );
}
