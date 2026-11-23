import { zodResolver } from '@hookform/resolvers/zod';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { toApiError, type ApiError } from '../../api/errors';
import { useNotify } from '../../app/notifications';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { studentAssignmentFilePath, studentOwnFilePath } from '../../features/assignments/api';
import { FileList } from '../../features/assignments/components/FileList';
import { FilePicker } from '../../features/assignments/components/FilePicker';
import { SubmissionStatusChip } from '../../features/assignments/components/StatusChips';
import {
  useRemoveSubmissionFile,
  useStudentAssignment,
  useSubmitWork,
} from '../../features/assignments/hooks';
import { formatDateTime } from '../../features/assignments/labels';
import { answerSchema, type AnswerFormValues } from '../../schemas/assignments';
import { fieldProps } from '../../utils/forms';

const MAX_FILES = 5;

export function StudentAssignmentDetailPage() {
  const { id = '' } = useParams();
  const notify = useNotify();
  const assignment = useStudentAssignment(id);
  const submit = useSubmitWork();
  const removeFile = useRemoveSubmissionFile();
  const [files, setFiles] = useState<File[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<AnswerFormValues>({
    resolver: zodResolver(answerSchema),
    values: { textAnswer: assignment.data?.submission?.textAnswer ?? '' },
    resetOptions: { keepDirtyValues: true },
  });

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

  const a = assignment.data;
  const mine = a.submission;
  const reviewed = mine?.status === 'REVIEWED';
  const overdue = new Date(a.dueAt) < new Date();
  const uploaded = mine?.attachments.length ?? 0;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await submit.mutateAsync({ id, textAnswer: values.textAnswer, files });
      setFiles([]);
      notify.success(mine ? 'Submission updated' : 'Submitted');
    } catch (cause) {
      setError(toApiError(cause));
    }
  });

  return (
    <>
      <Button
        component={RouterLink}
        to="/student/assignments"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 1 }}
      >
        All assignments
      </Button>
      <PageHeader title={a.title} subtitle={`${a.subjectName} · ${a.teacherName ?? 'Teacher'}`} />

      <Stack spacing={3}>
        <Card>
          <CardHeader title="Instructions" />
          <CardContent>
            <Stack spacing={1.5}>
              <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {a.description ?? 'No instructions.'}
              </Typography>
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                <StatusChip
                  label={`Due ${formatDateTime(a.dueAt)}`}
                  tone={overdue && !mine ? 'warning' : 'default'}
                />
                <StatusChip label={`${a.maxMarks} marks`} />
                {a.status === 'CLOSED' ? <StatusChip label="Closed" /> : null}
              </Stack>
              <FileList
                attachments={a.attachments}
                label="Files from your teacher"
                pathFor={(attachmentId) => studentAssignmentFilePath(id, attachmentId)}
              />
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            title="Your work"
            action={<SubmissionStatusChip status={mine?.status ?? null} />}
          />
          <CardContent>
            <Stack spacing={2}>
              {mine ? (
                <Typography variant="body2" color="text.secondary">
                  Last handed in {formatDateTime(mine.submittedAt)}
                </Typography>
              ) : null}
              {reviewed ? (
                <Alert severity="success">
                  Marks: {mine.marks} / {a.maxMarks}
                  {mine.feedback ? ` · ${mine.feedback}` : ''}
                </Alert>
              ) : null}
              {mine?.status === 'RESUBMISSION_REQUESTED' ? (
                <Alert severity="warning">
                  Your teacher asked you to try again{mine.feedback ? `: ${mine.feedback}` : '.'}
                </Alert>
              ) : null}
              {!a.canSubmit && !mine ? (
                <Alert severity="info">
                  {a.status === 'CLOSED'
                    ? 'This assignment is closed, so it no longer takes submissions.'
                    : 'The due date has passed and late submissions are not accepted.'}
                </Alert>
              ) : null}
              {a.canSubmit && overdue && mine?.status !== 'RESUBMISSION_REQUESTED' ? (
                <Alert severity="warning">
                  The due date has passed. Your work will be marked as late.
                </Alert>
              ) : null}

              {mine ? (
                <FileList
                  attachments={mine.attachments}
                  label="Your files"
                  pathFor={(attachmentId) => studentOwnFilePath(id, attachmentId)}
                  removing={removeFile.isPending}
                  onRemove={
                    a.canSubmit
                      ? (attachment) =>
                          removeFile.mutate(
                            { id, attachmentId: attachment.id },
                            {
                              onSuccess: () => notify.success('File removed'),
                              onError: (failure) => notify.error(failure.message),
                            },
                          )
                      : undefined
                  }
                />
              ) : null}

              {a.canSubmit ? (
                <Stack
                  component="form"
                  onSubmit={onSubmit}
                  noValidate
                  spacing={2}
                  aria-label="Your answer"
                >
                  {error ? <Alert severity="error">{error.message}</Alert> : null}
                  <TextField
                    label="Your answer"
                    fullWidth
                    multiline
                    minRows={4}
                    {...fieldProps(form, 'textAnswer')}
                  />
                  <FilePicker
                    files={files}
                    onChange={setFiles}
                    remaining={Math.max(0, MAX_FILES - uploaded)}
                    disabled={submit.isPending}
                  />
                  <Box>
                    <Button type="submit" variant="contained" loading={submit.isPending}>
                      {mine ? 'Update submission' : 'Submit'}
                    </Button>
                  </Box>
                </Stack>
              ) : mine && !reviewed && mine.textAnswer ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Your answer
                  </Typography>
                  <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {mine.textAnswer}
                  </Typography>
                </Box>
              ) : reviewed && mine.textAnswer ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Your answer
                  </Typography>
                  <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {mine.textAnswer}
                  </Typography>
                </Box>
              ) : null}
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
