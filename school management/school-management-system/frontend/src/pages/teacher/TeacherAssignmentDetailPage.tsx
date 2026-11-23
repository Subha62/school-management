import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import UploadIcon from '@mui/icons-material/Upload';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { toApiError, type ApiError } from '../../api/errors';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { teacherAssignmentFilePath } from '../../features/assignments/api';
import { AssignmentFormDialog } from '../../features/assignments/components/AssignmentFormDialog';
import { FileList } from '../../features/assignments/components/FileList';
import { FilePicker } from '../../features/assignments/components/FilePicker';
import { ReviewDialog } from '../../features/assignments/components/ReviewDialog';
import {
  AssignmentStatusChip,
  SubmissionStatusChip,
} from '../../features/assignments/components/StatusChips';
import {
  useAddAssignmentFiles,
  useAssignment,
  useDeleteAssignment,
  useRemoveAssignmentFile,
  useRoster,
  useTransitionAssignment,
} from '../../features/assignments/hooks';
import { formatDateTime } from '../../features/assignments/labels';
import type { Assignment, RosterRow } from '../../types/assignments';

type Action = 'publish' | 'close' | 'reopen' | 'archive' | 'delete';

const CONFIRM: Record<
  Action,
  { title: string; message: string; label: string; destructive?: boolean }
> = {
  publish: {
    title: 'Publish this assignment?',
    message: 'Students of the class will see it and can start submitting.',
    label: 'Publish',
  },
  close: {
    title: 'Close this assignment?',
    message: 'Students can no longer submit. You can still review what has been handed in.',
    label: 'Close',
  },
  reopen: {
    title: 'Reopen this assignment?',
    message: 'Students can submit again. Extend the due date first if it has passed.',
    label: 'Reopen',
  },
  archive: {
    title: 'Archive this assignment?',
    message: 'It disappears from your list and from students. Work already reviewed is kept.',
    label: 'Archive',
    destructive: true,
  },
  delete: {
    title: 'Delete this draft?',
    message: 'The draft and its files are deleted permanently.',
    label: 'Delete',
    destructive: true,
  },
};

const DONE: Record<Action, string> = {
  publish: 'Assignment published',
  close: 'Assignment closed',
  reopen: 'Assignment reopened',
  archive: 'Assignment archived',
  delete: 'Draft deleted',
};

function Roster({ rows, onReview }: { rows: RosterRow[]; onReview: (row: RosterRow) => void }) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  if (rows.length === 0) {
    return <Typography color="text.secondary">No students are enrolled in this class.</Typography>;
  }
  if (!wide) {
    return (
      <Stack
        component="ul"
        spacing={1}
        aria-label="Student work"
        sx={{ m: 0, p: 0, listStyle: 'none' }}
      >
        {rows.map((row) => (
          <Card key={row.studentId} component="li" variant="outlined">
            <CardContent sx={{ '&:last-child': { pb: 2 } }}>
              <Stack
                direction="row"
                spacing={1}
                sx={{ justifyContent: 'space-between', alignItems: 'start' }}
              >
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                    {row.studentName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {row.admissionNumber}
                    {row.submittedAt ? ` · ${formatDateTime(row.submittedAt)}` : ''}
                  </Typography>
                </Box>
                <SubmissionStatusChip status={row.status} />
              </Stack>
              {row.submissionId ? (
                <Button
                  size="small"
                  sx={{ mt: 1 }}
                  aria-label={`Review ${row.studentName}`}
                  onClick={() => onReview(row)}
                >
                  {row.status === 'REVIEWED' ? `Marks ${row.marks ?? '-'} · Open` : 'Review'}
                </Button>
              ) : null}
            </CardContent>
          </Card>
        ))}
      </Stack>
    );
  }
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small" aria-label="Student work">
        <TableHead>
          <TableRow>
            <TableCell>Roll</TableCell>
            <TableCell>Student</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Handed in</TableCell>
            <TableCell align="right">Marks</TableCell>
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.studentId}>
              <TableCell>{row.rollNumber ?? '-'}</TableCell>
              <TableCell>
                {row.studentName} ({row.admissionNumber})
              </TableCell>
              <TableCell>
                <SubmissionStatusChip status={row.status} />
              </TableCell>
              <TableCell>{row.submittedAt ? formatDateTime(row.submittedAt) : '-'}</TableCell>
              <TableCell align="right">{row.marks ?? '-'}</TableCell>
              <TableCell align="right">
                {row.submissionId ? (
                  <Button
                    size="small"
                    aria-label={`Review ${row.studentName}`}
                    onClick={() => onReview(row)}
                  >
                    {row.status === 'REVIEWED' ? 'Open' : 'Review'}
                  </Button>
                ) : null}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export function TeacherAssignmentDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const notify = useNotify();
  const assignment = useAssignment(id);
  const roster = useRoster(id);
  const transition = useTransitionAssignment();
  const remove = useDeleteAssignment();
  const addFiles = useAddAssignmentFiles();
  const removeFile = useRemoveAssignmentFile();
  const [editing, setEditing] = useState(false);
  const [confirm, setConfirm] = useState<Action | null>(null);
  const [reviewing, setReviewing] = useState<RosterRow | null>(null);
  const [chosen, setChosen] = useState<File[]>([]);
  const [uploadError, setUploadError] = useState<ApiError | null>(null);

  if (assignment.isPending) {
    return <LoadingState />;
  }
  if (assignment.isError) {
    return (
      <ErrorState
        title="Unable to load the assignment"
        error={assignment.error}
        onRetry={() => void assignment.refetch()}
      />
    );
  }

  const a: Assignment = assignment.data;
  const editable = a.status !== 'ARCHIVED';
  const filesOpen = a.status === 'DRAFT' || a.status === 'PUBLISHED';
  const pending = confirm === 'delete' ? remove : transition;
  const config = confirm ? CONFIRM[confirm] : null;

  const upload = async () => {
    setUploadError(null);
    try {
      await addFiles.mutateAsync({ id, files: chosen });
      setChosen([]);
      notify.success(chosen.length === 1 ? 'File added' : 'Files added');
    } catch (cause) {
      setUploadError(toApiError(cause));
    }
  };

  return (
    <>
      <Button
        component={RouterLink}
        to="/teacher/assignments"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 1 }}
      >
        All assignments
      </Button>
      <PageHeader
        title={a.title}
        subtitle={`${a.sectionName} · ${a.subjectName}`}
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {editable ? (
              <Button variant="outlined" startIcon={<EditIcon />} onClick={() => setEditing(true)}>
                Edit
              </Button>
            ) : null}
            {a.status === 'DRAFT' ? (
              <>
                <Button color="error" onClick={() => setConfirm('delete')}>
                  Delete
                </Button>
                <Button variant="contained" onClick={() => setConfirm('publish')}>
                  Publish
                </Button>
              </>
            ) : null}
            {a.status === 'PUBLISHED' ? (
              <Button variant="contained" onClick={() => setConfirm('close')}>
                Close
              </Button>
            ) : null}
            {a.status === 'CLOSED' ? (
              <>
                <Button onClick={() => setConfirm('archive')}>Archive</Button>
                <Button variant="contained" onClick={() => setConfirm('reopen')}>
                  Reopen
                </Button>
              </>
            ) : null}
          </Stack>
        }
      />

      <Stack spacing={3}>
        <Card>
          <CardHeader title="Details" action={<AssignmentStatusChip status={a.status} />} />
          <CardContent>
            <Stack spacing={1.5}>
              <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {a.description ?? 'No instructions.'}
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                <StatusChip label={`Due ${formatDateTime(a.dueAt)}`} />
                <StatusChip label={`${a.maxMarks} marks`} />
                <StatusChip label={a.allowLate ? 'Late work accepted' : 'No late work'} />
              </Stack>
              {a.status === 'DRAFT' ? (
                <Alert severity="info">
                  This is a draft. Students cannot see it until you publish it.
                </Alert>
              ) : null}
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Files for students" />
          <CardContent>
            <Stack spacing={2}>
              <FileList
                attachments={a.attachments}
                label="Assignment files"
                pathFor={(attachmentId) => teacherAssignmentFilePath(id, attachmentId)}
                removing={removeFile.isPending}
                onRemove={
                  filesOpen
                    ? (attachment) =>
                        removeFile.mutate(
                          { id, attachmentId: attachment.id },
                          {
                            onSuccess: () => notify.success('File removed'),
                            onError: (error) => notify.error(error.message),
                          },
                        )
                    : undefined
                }
              />
              {filesOpen ? (
                <>
                  <FilePicker
                    files={chosen}
                    onChange={setChosen}
                    remaining={Math.max(0, 10 - a.attachments.length)}
                    disabled={addFiles.isPending}
                  />
                  {uploadError ? <Alert severity="error">{uploadError.message}</Alert> : null}
                  {chosen.length > 0 ? (
                    <Box>
                      <Button
                        variant="contained"
                        startIcon={<UploadIcon />}
                        loading={addFiles.isPending}
                        onClick={() => void upload()}
                      >
                        Upload {chosen.length} {chosen.length === 1 ? 'file' : 'files'}
                      </Button>
                    </Box>
                  ) : null}
                </>
              ) : null}
            </Stack>
          </CardContent>
        </Card>

        {a.status !== 'DRAFT' ? (
          <Card>
            <CardHeader
              title="Student work"
              subheader={`${a.counts.submitted} of ${a.counts.enrolled} handed in · ${a.counts.awaitingReview} waiting for review · ${a.counts.reviewed} reviewed`}
            />
            <CardContent>
              {roster.isPending ? <LoadingState /> : null}
              {roster.isError ? (
                <ErrorState error={roster.error} onRetry={() => void roster.refetch()} />
              ) : null}
              {roster.data ? <Roster rows={roster.data} onReview={setReviewing} /> : null}
            </CardContent>
          </Card>
        ) : null}
      </Stack>

      {editing ? <AssignmentFormDialog assignment={a} onClose={() => setEditing(false)} /> : null}
      {reviewing?.submissionId ? (
        <ReviewDialog
          assignmentId={id}
          submissionId={reviewing.submissionId}
          maxMarks={a.maxMarks}
          onClose={() => setReviewing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={config !== null}
        title={config?.title ?? ''}
        message={config?.message ?? ''}
        confirmLabel={config?.label ?? 'Confirm'}
        destructive={config?.destructive}
        loading={pending.isPending}
        error={pending.error}
        onCancel={() => {
          pending.reset();
          setConfirm(null);
        }}
        onConfirm={() => {
          if (!confirm) return;
          if (confirm === 'delete') {
            remove.mutate(id, {
              onSuccess: () => {
                notify.success(DONE.delete);
                void navigate('/teacher/assignments');
              },
            });
          } else {
            transition.mutate(
              { id, action: confirm },
              {
                onSuccess: () => {
                  notify.success(DONE[confirm]);
                  setConfirm(null);
                },
              },
            );
          }
        }}
      />
    </>
  );
}
