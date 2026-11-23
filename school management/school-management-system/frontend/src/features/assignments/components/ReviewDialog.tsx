import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ErrorState } from '../../../components/ErrorState';
import { LoadingState } from '../../../components/LoadingState';
import {
  reviewSchema,
  type ReviewChoice,
  type ReviewFormValues,
} from '../../../schemas/assignments';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { teacherSubmissionFilePath } from '../api';
import { useReviewSubmission, useSubmission } from '../hooks';
import { formatDateTime } from '../labels';
import { FileList } from './FileList';
import { SubmissionStatusChip } from './StatusChips';

const FORM_ID = 'review-form';

interface ReviewDialogProps {
  assignmentId: string;
  submissionId: string;
  maxMarks: number;
  onClose: () => void;
}

/** The student's answer and files next to the review form: marks and feedback, or a request to try again. */
export function ReviewDialog({ assignmentId, submissionId, maxMarks, onClose }: ReviewDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const submission = useSubmission(assignmentId, submissionId);

  return (
    <Dialog
      open
      onClose={onClose}
      fullScreen={fullScreen}
      fullWidth
      maxWidth="md"
      aria-labelledby={`${FORM_ID}-title`}
    >
      <DialogTitle id={`${FORM_ID}-title`}>
        {submission.data ? `Review: ${submission.data.studentName}` : 'Review submission'}
      </DialogTitle>
      {submission.isPending ? (
        <DialogContent dividers>
          <LoadingState />
        </DialogContent>
      ) : submission.isError ? (
        <>
          <DialogContent dividers>
            <ErrorState error={submission.error} onRetry={() => void submission.refetch()} />
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={onClose}>Close</Button>
          </DialogActions>
        </>
      ) : (
        <ReviewForm
          key={submission.data.version}
          assignmentId={assignmentId}
          maxMarks={maxMarks}
          submission={submission.data}
          onClose={onClose}
        />
      )}
    </Dialog>
  );
}

function ReviewForm({
  assignmentId,
  maxMarks,
  submission,
  onClose,
}: {
  assignmentId: string;
  maxMarks: number;
  submission: NonNullable<ReturnType<typeof useSubmission>['data']>;
  onClose: () => void;
}) {
  const review = useReviewSubmission();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const choice = useRef<ReviewChoice>('REVIEWED');
  const waiting = submission.status === 'RESUBMISSION_REQUESTED';
  const form = useForm<ReviewFormValues>({
    // The rules depend on which button was pressed, so the schema is chosen at validation time.
    resolver: (values, context, options) =>
      zodResolver(reviewSchema(choice.current, maxMarks))(values, context, options),
    defaultValues: {
      marks: submission.marks === null ? '' : String(submission.marks),
      feedback: submission.feedback ?? '',
    },
  });

  const submitAs = (outcome: ReviewChoice) => {
    choice.current = outcome;
    void form.handleSubmit(async (values) => {
      setError(null);
      try {
        await review.mutateAsync({
          assignmentId,
          submissionId: submission.id,
          body: {
            outcome,
            marks: outcome === 'REVIEWED' ? Number(values.marks) : null,
            feedback: values.feedback === '' ? null : values.feedback,
            version: submission.version,
          },
        });
        notify.success(outcome === 'REVIEWED' ? 'Review saved' : 'Resubmission requested');
        onClose();
      } catch (cause) {
        const apiError = toApiError(cause);
        const onField = applyServerFieldErrors(form, apiError, ['marks', 'feedback']);
        setError(onField ? null : apiError);
      }
    })();
  };

  return (
    <>
      <DialogContent dividers>
        <Stack
          component="form"
          id={FORM_ID}
          noValidate
          spacing={2.5}
          onSubmit={(event) => {
            event.preventDefault();
            submitAs('REVIEWED');
          }}
        >
          {error ? <Alert severity="error">{error.message}</Alert> : null}
          <Stack
            direction="row"
            spacing={1}
            sx={{ alignItems: 'center', flexWrap: 'wrap' }}
            useFlexGap
          >
            <SubmissionStatusChip status={submission.status} />
            <Typography variant="body2" color="text.secondary">
              {submission.admissionNumber} · handed in {formatDateTime(submission.submittedAt)}
            </Typography>
          </Stack>
          {waiting ? (
            <Alert severity="info">
              You asked for a resubmission. The student has not answered again yet.
            </Alert>
          ) : null}
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              Answer
            </Typography>
            {submission.textAnswer ? (
              <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {submission.textAnswer}
              </Typography>
            ) : (
              <Typography color="text.secondary">No written answer.</Typography>
            )}
          </Box>
          <Box>
            <Typography variant="subtitle2" gutterBottom>
              Files
            </Typography>
            <FileList
              attachments={submission.attachments}
              label="Submitted files"
              pathFor={(attachmentId) =>
                teacherSubmissionFilePath(assignmentId, submission.id, attachmentId)
              }
            />
          </Box>
          <TextField
            label={`Marks (out of ${maxMarks})`}
            fullWidth
            disabled={waiting}
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            {...fieldProps(form, 'marks')}
          />
          <TextField
            label="Feedback"
            fullWidth
            multiline
            minRows={3}
            disabled={waiting}
            {...fieldProps(form, 'feedback')}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2, flexWrap: 'wrap', gap: 1 }}>
        <Button onClick={onClose} disabled={form.formState.isSubmitting}>
          Cancel
        </Button>
        <Button
          color="warning"
          disabled={waiting || form.formState.isSubmitting}
          onClick={() => submitAs('RESUBMISSION_REQUESTED')}
        >
          Request resubmission
        </Button>
        <Button
          variant="contained"
          disabled={waiting}
          loading={form.formState.isSubmitting}
          onClick={() => submitAs('REVIEWED')}
        >
          Save review
        </Button>
      </DialogActions>
    </>
  );
}
