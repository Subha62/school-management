import Alert from '@mui/material/Alert';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { AttendanceKind } from '../../features/operations/components/AttendanceKind';
import { ClassFilter } from '../../features/operations/components/ClassFilter';
import { ReportTable } from '../../features/operations/components/ReportTable';
import { SessionDetailDialog } from '../../features/operations/components/SessionDetailDialog';
import {
  useAdminMonthly,
  useAdminSessions,
  useBelowThreshold,
} from '../../features/operations/hooks';
import { TYPE_LABELS } from '../../features/operations/labels';
import type { AttendanceSummary, SessionStatus, SessionType } from '../../types/operations';
import { currentMonth, todayIso } from '../../utils/dates';
import { formatDate } from '../../utils/format';

type ReportTab = 'monthly' | 'below' | 'sessions';

function MonthlyReport() {
  const [sectionId, setSectionId] = useState('');
  const [month, setMonth] = useState(currentMonth());
  const [type, setType] = useState<SessionType>('DAILY');
  const [subjectId, setSubjectId] = useState('');
  const report = useAdminMonthly({ sectionId, month, type, subjectId: subjectId || undefined });

  return (
    <>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <ClassFilter sectionId={sectionId} onChange={setSectionId} />
        <TextField
          size="small"
          type="month"
          label="Month"
          value={month}
          onChange={(event) => event.target.value && setMonth(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <AttendanceKind
          type={type}
          subjectId={subjectId}
          sectionId={sectionId}
          onType={setType}
          onSubject={setSubjectId}
        />
      </Stack>
      {!sectionId ? (
        <Alert severity="info">Choose a class to see its monthly attendance.</Alert>
      ) : type === 'SUBJECT' && !subjectId ? (
        <Alert severity="info">Choose a subject.</Alert>
      ) : report.isPending ? (
        <LoadingState />
      ) : report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <>
          <Alert severity="info" sx={{ mb: 2 }}>
            Percentages use finalized sessions only. Late counts as present, a half day as half, and
            excused days are left out. The warning line is {report.data.threshold}%.
          </Alert>
          <ReportTable
            rows={report.data.rows}
            caption="Monthly attendance"
            emptyTitle="No students"
            emptyHint="No students are enrolled in this class."
          />
        </>
      )}
    </>
  );
}

function BelowThresholdReport() {
  const [sectionId, setSectionId] = useState('');
  const [from, setFrom] = useState(() => `${currentMonth()}-01`);
  const [to, setTo] = useState(todayIso());
  const [type, setType] = useState<SessionType>('DAILY');
  const [subjectId, setSubjectId] = useState('');
  const [threshold, setThreshold] = useState('');
  const thresholdValue = threshold.trim() === '' ? undefined : Number(threshold);
  const invalidThreshold =
    thresholdValue !== undefined &&
    (!Number.isInteger(thresholdValue) || thresholdValue < 1 || thresholdValue > 100);
  const report = useBelowThreshold({
    from: from || undefined,
    to: to || undefined,
    sectionId: sectionId || undefined,
    type,
    subjectId: subjectId || undefined,
    threshold: invalidThreshold ? undefined : thresholdValue,
  });

  return (
    <>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <TextField
          size="small"
          type="date"
          label="From"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          size="small"
          type="date"
          label="To"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <ClassFilter sectionId={sectionId} onChange={setSectionId} allowAll />
        <AttendanceKind
          type={type}
          subjectId={subjectId}
          sectionId={sectionId}
          onType={setType}
          onSubject={setSubjectId}
        />
        <TextField
          size="small"
          label="Below (%)"
          value={threshold}
          placeholder="75"
          onChange={(event) => setThreshold(event.target.value)}
          error={invalidThreshold}
          helperText={invalidThreshold ? 'Enter a whole number from 1 to 100' : undefined}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          sx={{ width: 120 }}
        />
      </Stack>
      {type === 'SUBJECT' && !subjectId ? (
        <Alert severity="info">Choose a class and a subject for subject-wise attendance.</Alert>
      ) : report.isPending ? (
        <LoadingState />
      ) : report.isError ? (
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      ) : (
        <>
          {report.data.truncated ? (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Only the 500 lowest students are shown. Narrow the class or the period to see the
              rest.
            </Alert>
          ) : null}
          <ReportTable
            rows={report.data.rows}
            caption="Students below the attendance threshold"
            emptyTitle="Nobody is below the threshold"
            emptyHint={`No student is under ${report.data.threshold}% between ${formatDate(report.data.from)} and ${formatDate(report.data.to)}.`}
            showClass
          />
        </>
      )}
    </>
  );
}

function SessionList() {
  const [sectionId, setSectionId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [viewing, setViewing] = useState<string | null>(null);
  const sessions = useAdminSessions({
    page,
    size,
    sectionId: sectionId || undefined,
    from: from || undefined,
    to: to || undefined,
    status: (status || undefined) as SessionStatus | undefined,
  });

  const columns: Column<AttendanceSummary>[] = [
    { id: 'date', header: 'Date', render: (s) => formatDate(s.date) },
    { id: 'class', header: 'Class', render: (s) => s.sectionName },
    {
      id: 'type',
      header: 'Attendance',
      hideBelow: 'sm',
      render: (s) => (s.subjectName ? s.subjectName : TYPE_LABELS[s.type]),
    },
    {
      id: 'counts',
      header: 'Present / absent',
      hideBelow: 'md',
      render: (s) => `${s.counts.present + s.counts.late + s.counts.halfDay} / ${s.counts.absent}`,
    },
    {
      id: 'status',
      header: 'Status',
      render: (s) => (
        <StatusChip
          label={s.status === 'FINALIZED' ? 'Finalized' : 'Open'}
          tone={s.status === 'FINALIZED' ? 'success' : 'warning'}
        />
      ),
    },
  ];
  const actions: RowAction<AttendanceSummary>[] = [
    { label: 'Review or correct', onSelect: (summary) => setViewing(summary.id) },
  ];
  const reset = () => setPage(0);

  return (
    <>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <ClassFilter
          sectionId={sectionId}
          onChange={(value) => {
            setSectionId(value);
            reset();
          }}
          allowAll
        />
        <TextField
          size="small"
          type="date"
          label="From"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
            reset();
          }}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          size="small"
          type="date"
          label="To"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
            reset();
          }}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            reset();
          }}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          <MenuItem value="OPEN">Open</MenuItem>
          <MenuItem value="FINALIZED">Finalized</MenuItem>
        </TextField>
      </Stack>
      <DataList
        data={sessions.data}
        columns={columns}
        actions={actions}
        getRowId={(summary) => summary.id}
        getRowLabel={(summary) => `${summary.sectionName} ${summary.date}`}
        isLoading={sessions.isPending}
        error={sessions.error}
        onRetry={() => void sessions.refetch()}
        emptyTitle="No attendance sessions"
        emptyHint="Sessions opened by teachers appear here."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
      {viewing ? (
        <SessionDetailDialog sessionId={viewing} onClose={() => setViewing(null)} />
      ) : null}
    </>
  );
}

export function AttendanceReportsPage() {
  const [tab, setTab] = useState<ReportTab>('monthly');

  return (
    <>
      <PageHeader
        title="Attendance reports"
        subtitle="Monthly percentages, students below the threshold, and corrections to finalized registers."
      />
      <Tabs
        value={tab}
        onChange={(_event, value: ReportTab) => setTab(value)}
        variant="scrollable"
        scrollButtons="auto"
        aria-label="Attendance report"
        sx={{ mb: 2 }}
      >
        <Tab value="monthly" label="Monthly" />
        <Tab value="below" label="Below threshold" />
        <Tab value="sessions" label="Sessions and corrections" />
      </Tabs>
      {tab === 'monthly' ? <MonthlyReport /> : null}
      {tab === 'below' ? <BelowThresholdReport /> : null}
      {tab === 'sessions' ? <SessionList /> : null}
    </>
  );
}
