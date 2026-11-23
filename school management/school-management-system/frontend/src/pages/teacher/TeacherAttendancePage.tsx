import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { ApiError } from '../../api/errors';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { AttendanceSheet } from '../../features/operations/components/AttendanceSheet';
import { ReportTable } from '../../features/operations/components/ReportTable';
import {
  useDeleteSession,
  useFinalizeSession,
  useMarkAttendance,
  useMyClasses,
  useMySession,
  useMySessions,
  useOpenSession,
  useTeacherMonthly,
} from '../../features/operations/hooks';
import { TYPE_LABELS } from '../../features/operations/labels';
import type { AttendanceSummary, SessionType } from '../../types/operations';
import { currentMonth, todayIso } from '../../utils/dates';
import { formatDate } from '../../utils/format';

const DAILY = 'DAILY';

export function TeacherAttendancePage() {
  const notify = useNotify();
  const classes = useMyClasses();
  const [classChoice, setClassChoice] = useState('');
  const [scopeChoice, setScopeChoice] = useState('');
  const [date, setDate] = useState(todayIso());
  const [period, setPeriod] = useState('');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sheetError, setSheetError] = useState<ApiError | null>(null);
  const [openError, setOpenError] = useState<ApiError | null>(null);
  const [confirm, setConfirm] = useState<'finalize' | 'delete' | null>(null);
  const [month, setMonth] = useState(currentMonth());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  const options = classes.data ?? [];
  const selectedClass = options.find((option) => option.sectionId === classChoice) ?? options[0];
  const scopes = selectedClass
    ? [
        ...(selectedClass.daily ? [{ value: DAILY, label: 'Whole day (daily attendance)' }] : []),
        ...selectedClass.subjects.map((subject) => ({ value: subject.id, label: subject.name })),
      ]
    : [];
  const scope = scopes.some((option) => option.value === scopeChoice)
    ? scopeChoice
    : (scopes[0]?.value ?? '');
  const subjectScope = scope !== DAILY && scope !== '';
  const reportType: SessionType = subjectScope ? 'SUBJECT' : 'DAILY';

  const open = useOpenSession();
  const mark = useMarkAttendance();
  const finalize = useFinalizeSession();
  const remove = useDeleteSession();
  const session = useMySession(sessionId);
  const recent = useMySessions({ page, size });
  const report = useTeacherMonthly({
    sectionId: selectedClass?.sectionId ?? '',
    month,
    type: reportType,
    subjectId: subjectScope ? scope : undefined,
  });

  const openRegister = () => {
    if (!selectedClass) return;
    setOpenError(null);
    setSheetError(null);
    open.mutate(
      {
        sectionId: selectedClass.sectionId,
        date,
        subjectId: subjectScope ? scope : null,
        periodNumber: subjectScope && period.trim() !== '' ? Number(period) : null,
      },
      {
        onSuccess: (detail) => setSessionId(detail.session.id),
        onError: (error) => setOpenError(error),
      },
    );
  };

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
      id: 'marked',
      header: 'Marked',
      hideBelow: 'md',
      render: (s) =>
        `${s.counts.present + s.counts.absent + s.counts.late + s.counts.halfDay + s.counts.excused} / ${s.enrolled}`,
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
    {
      label: 'Open',
      onSelect: (summary) => {
        setSheetError(null);
        setSessionId(summary.id);
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="Attendance"
        subtitle="Take the register for your classes. Finalize it when everyone is marked."
      />

      {classes.isPending ? <LoadingState /> : null}
      {classes.isError ? (
        <ErrorState error={classes.error} onRetry={() => void classes.refetch()} />
      ) : null}
      {classes.data && options.length === 0 ? (
        <Alert severity="info">
          You are not the class teacher or a subject teacher of any class in the active academic
          year, so there is no attendance for you to take.
        </Alert>
      ) : null}

      {selectedClass ? (
        <Stack spacing={3}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={2}
            useFlexGap
            sx={{ alignItems: { md: 'center' } }}
          >
            <TextField
              select
              size="small"
              label="Class"
              value={selectedClass.sectionId}
              onChange={(event) => {
                setClassChoice(event.target.value);
                setScopeChoice('');
              }}
              sx={{ minWidth: 200 }}
            >
              {options.map((option) => (
                <MenuItem key={option.sectionId} value={option.sectionId}>
                  {option.sectionName}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Attendance for"
              value={scope}
              onChange={(event) => setScopeChoice(event.target.value)}
              sx={{ minWidth: 240 }}
            >
              {scopes.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              size="small"
              type="date"
              label="Date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: todayIso() } }}
            />
            {subjectScope ? (
              <TextField
                size="small"
                label="Period (optional)"
                value={period}
                onChange={(event) => setPeriod(event.target.value)}
                slotProps={{ htmlInput: { inputMode: 'numeric' } }}
                sx={{ width: { md: 150 } }}
              />
            ) : null}
            <Button
              variant="contained"
              loading={open.isPending}
              disabled={!date || !scope}
              onClick={openRegister}
            >
              Open register
            </Button>
          </Stack>
          {openError ? <Alert severity="error">{openError.message}</Alert> : null}

          {sessionId ? (
            session.isPending ? (
              <LoadingState />
            ) : session.isError ? (
              <ErrorState error={session.error} onRetry={() => void session.refetch()} />
            ) : (
              <AttendanceSheet
                key={session.data.session.id}
                detail={session.data}
                saving={mark.isPending}
                finalizing={finalize.isPending}
                error={sheetError}
                onSave={(records) => {
                  setSheetError(null);
                  mark.mutate(
                    { id: session.data.session.id, records },
                    {
                      onSuccess: () => notify.success('Attendance saved'),
                      onError: (error) => setSheetError(error),
                    },
                  );
                }}
                onFinalize={() => setConfirm('finalize')}
                onDelete={() => setConfirm('delete')}
              />
            )
          ) : null}

          <Box>
            <Typography variant="h6" component="h2" gutterBottom>
              Monthly summary
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
              <TextField
                size="small"
                type="month"
                label="Month"
                value={month}
                onChange={(event) => setMonth(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <Typography variant="body2" color="text.secondary" sx={{ alignSelf: 'center' }}>
                {selectedClass.sectionName},{' '}
                {subjectScope
                  ? (scopes.find((s) => s.value === scope)?.label ?? '')
                  : 'daily attendance'}
                . Only finalized sessions count.
              </Typography>
            </Stack>
            {report.isPending && report.fetchStatus !== 'idle' ? <LoadingState /> : null}
            {report.isError ? (
              <Alert severity="warning">
                {report.error.status === 403
                  ? 'You can see the summary of your own subject, or of the whole class if you are its class teacher.'
                  : report.error.message}
              </Alert>
            ) : null}
            {report.data ? (
              <ReportTable
                rows={report.data.rows}
                caption="Monthly attendance"
                emptyTitle="No students"
                emptyHint="No students are enrolled in this class."
              />
            ) : null}
          </Box>

          <Box>
            <Typography variant="h6" component="h2" gutterBottom>
              Recent sessions
            </Typography>
            <DataList
              data={recent.data}
              columns={columns}
              actions={actions}
              getRowId={(summary) => summary.id}
              getRowLabel={(summary) => `${summary.sectionName} ${summary.date}`}
              isLoading={recent.isPending}
              error={recent.error}
              onRetry={() => void recent.refetch()}
              emptyTitle="No sessions yet"
              emptyHint="Registers you open appear here."
              onPageChange={setPage}
              onPageSizeChange={(newSize) => {
                setSize(newSize);
                setPage(0);
              }}
            />
          </Box>
        </Stack>
      ) : null}

      <ConfirmDialog
        open={confirm === 'finalize'}
        title="Finalize attendance?"
        message="After finalizing, only an administrator can change this register."
        confirmLabel="Finalize"
        loading={finalize.isPending}
        error={finalize.error}
        onCancel={() => {
          finalize.reset();
          setConfirm(null);
        }}
        onConfirm={() => {
          if (!sessionId) return;
          finalize.mutate(sessionId, {
            onSuccess: () => {
              notify.success('Attendance finalized');
              setConfirm(null);
            },
          });
        }}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        title="Delete this register?"
        message="The marks entered so far will be lost. You can open the register again afterwards."
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={() => {
          remove.reset();
          setConfirm(null);
        }}
        onConfirm={() => {
          if (!sessionId) return;
          remove.mutate(sessionId, {
            onSuccess: () => {
              notify.success('Register deleted');
              setSessionId(null);
              setConfirm(null);
            },
          });
        }}
      />
    </>
  );
}
