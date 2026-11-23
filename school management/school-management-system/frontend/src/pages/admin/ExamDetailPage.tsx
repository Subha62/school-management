import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { ExamFormDialog } from '../../features/exams/components/ExamFormDialog';
import { ProgressPanel } from '../../features/exams/components/ProgressPanel';
import { ResponsiveRows, type RowColumn } from '../../features/exams/components/ResponsiveRows';
import { ExamStatusChip } from '../../features/exams/components/StatusChips';
import {
  useDeleteExam,
  useExam,
  useExamProgress,
  useTransitionExam,
} from '../../features/exams/hooks';
import {
  EXAM_ACTIONS,
  formatDateRange,
  formatInstant,
  formatNumber,
} from '../../features/exams/labels';
import type { ExamAction, ExamSubject } from '../../types/exams';
import { formatDate } from '../../utils/format';

type Pending = ExamAction | 'delete';

const subjectColumns: RowColumn<ExamSubject>[] = [
  {
    id: 'subject',
    header: 'Subject',
    render: (row) => `${row.subjectName} (${row.subjectCode})`,
  },
  { id: 'max', header: 'Maximum marks', render: (row) => formatNumber(row.maxMarks) },
  { id: 'pass', header: 'Pass marks', render: (row) => formatNumber(row.passMarks) },
  {
    id: 'date',
    header: 'Exam date',
    render: (row) => (row.examDate ? formatDate(row.examDate) : 'Not set'),
  },
];

export function ExamDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotify();
  const exam = useExam(id);
  const showProgress = exam.data !== undefined && exam.data.status !== 'DRAFT';
  const progress = useExamProgress(id, showProgress);
  const transition = useTransitionExam();
  const remove = useDeleteExam();
  const [pending, setPending] = useState<Pending | null>(null);
  const [editing, setEditing] = useState(false);

  const back = (
    <Button
      component={RouterLink}
      to="/admin/exams"
      startIcon={<ArrowBackIcon />}
      sx={{ mb: 1, alignSelf: 'flex-start' }}
    >
      Exams
    </Button>
  );

  if (exam.isPending) {
    return (
      <>
        {back}
        <LoadingState label="Loading exam" />
      </>
    );
  }
  if (exam.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the exam"
          error={exam.error}
          onRetry={() => void exam.refetch()}
        />
      </>
    );
  }

  const data = exam.data;
  const missing = progress.data && !progress.data.complete ? progress.data.missing : 0;
  const cancel = () => {
    transition.reset();
    remove.reset();
    setPending(null);
  };

  const confirmation =
    pending === 'delete'
      ? {
          title: 'Delete this exam?',
          message: `${data.name} is deleted permanently. Only draft exams can be deleted.`,
          label: 'Delete',
          destructive: true,
          loading: remove.isPending,
          error: remove.error,
        }
      : pending
        ? {
            ...EXAM_ACTIONS[pending],
            message:
              pending === 'lock' && missing > 0
                ? `${EXAM_ACTIONS.lock.message} ${missing} ${missing === 1 ? 'entry is' : 'entries are'} still missing, so locking will be refused.`
                : EXAM_ACTIONS[pending].message,
            loading: transition.isPending,
            error: transition.error,
          }
        : null;

  const confirm = () => {
    if (pending === 'delete') {
      remove.mutate(data.id, {
        onSuccess: () => {
          notify.success('Exam deleted');
          void navigate('/admin/exams');
        },
      });
    } else if (pending) {
      const action = pending;
      transition.mutate(
        { id: data.id, action },
        {
          onSuccess: () => {
            notify.success(EXAM_ACTIONS[action].done);
            setPending(null);
          },
        },
      );
    }
  };

  const buttons = (
    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
      {data.status === 'DRAFT' ? (
        <>
          <Button variant="outlined" startIcon={<EditIcon />} onClick={() => setEditing(true)}>
            Edit
          </Button>
          <Button variant="contained" onClick={() => setPending('open')}>
            Open for marks
          </Button>
          <Button
            color="error"
            variant="outlined"
            startIcon={<DeleteOutlineIcon />}
            onClick={() => setPending('delete')}
          >
            Delete
          </Button>
        </>
      ) : null}
      {data.status === 'OPEN_FOR_MARKS' ? (
        <Button variant="contained" onClick={() => setPending('lock')}>
          Lock marks
        </Button>
      ) : null}
      {data.status === 'LOCKED' ? (
        <>
          <Button variant="outlined" onClick={() => setPending('reopen')}>
            Reopen
          </Button>
          <Button variant="contained" onClick={() => setPending('publish')}>
            Publish results
          </Button>
        </>
      ) : null}
      {data.status === 'LOCKED' || data.status === 'PUBLISHED' ? (
        <Button
          component={RouterLink}
          to={`/admin/results?examId=${data.id}`}
          variant={data.status === 'PUBLISHED' ? 'contained' : 'outlined'}
        >
          {data.status === 'PUBLISHED' ? 'View results' : 'Preview results'}
        </Button>
      ) : null}
    </Stack>
  );

  return (
    <>
      {back}
      <PageHeader
        title={data.name}
        subtitle={`${data.academicYearName} · ${data.gradeLevelName}`}
        actions={buttons}
      />

      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, mb: 3 }}>
        <Stack
          component="dl"
          direction={{ xs: 'column', sm: 'row' }}
          spacing={{ xs: 1.5, sm: 4 }}
          useFlexGap
          sx={{ m: 0, flexWrap: 'wrap' }}
        >
          <Box>
            <Typography component="dt" variant="body2" color="text.secondary">
              Status
            </Typography>
            <Box component="dd" sx={{ m: 0, mt: 0.5 }}>
              <ExamStatusChip status={data.status} />
            </Box>
          </Box>
          <Box>
            <Typography component="dt" variant="body2" color="text.secondary">
              Dates
            </Typography>
            <Typography component="dd" sx={{ m: 0 }}>
              {formatDateRange(data.startDate, data.endDate, formatDate)}
            </Typography>
          </Box>
          {data.lockedAt ? (
            <Box>
              <Typography component="dt" variant="body2" color="text.secondary">
                Locked on
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {formatInstant(data.lockedAt)}
              </Typography>
            </Box>
          ) : null}
          {data.publishedAt ? (
            <Box>
              <Typography component="dt" variant="body2" color="text.secondary">
                Published on
              </Typography>
              <Typography component="dd" sx={{ m: 0 }}>
                {formatInstant(data.publishedAt)}
              </Typography>
            </Box>
          ) : null}
        </Stack>
      </Paper>

      <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>
        Subjects
      </Typography>
      <Box sx={{ mb: 3 }}>
        <ResponsiveRows
          label="Exam subjects"
          rows={data.subjects}
          columns={subjectColumns}
          getRowId={(row) => row.id}
        />
      </Box>

      <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>
        Marks entry progress
      </Typography>
      {showProgress ? (
        <ProgressPanel progress={progress} />
      ) : (
        <Typography color="text.secondary">
          Open the exam for marks to follow how far each teacher has got.
        </Typography>
      )}

      {editing ? <ExamFormDialog exam={data} onClose={() => setEditing(false)} /> : null}
      <ConfirmDialog
        open={confirmation !== null}
        title={confirmation?.title ?? ''}
        message={confirmation?.message ?? ''}
        confirmLabel={confirmation?.label ?? ''}
        destructive={confirmation?.destructive}
        loading={confirmation?.loading}
        error={confirmation?.error}
        onCancel={cancel}
        onConfirm={confirm}
      />
    </>
  );
}
