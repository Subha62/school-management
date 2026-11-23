import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { toApiError } from '../../api/errors';
import { useNotify } from '../../app/notifications';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { ExamStatusChip } from '../../features/exams/components/StatusChips';
import { useCommentSheet, useSaveComment } from '../../features/exams/hooks';
import { MAX_COMMENT, commentSchema } from '../../schemas/exams';
import type { CommentRow } from '../../types/exams';

function CommentEditor({
  examId,
  row,
  editable,
}: {
  examId: string;
  row: CommentRow;
  editable: boolean;
}) {
  const save = useSaveComment();
  const notify = useNotify();
  const [savedText, setSavedText] = useState(row.comment ?? '');
  const [text, setText] = useState(row.comment ?? '');
  const [error, setError] = useState<string | null>(null);
  const dirty = text.trim() !== savedText.trim();

  const submit = async () => {
    const parsed = commentSchema.safeParse({ comment: text });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? 'The comment is not valid');
      return;
    }
    setError(null);
    try {
      await save.mutateAsync({ examId, studentId: row.studentId, comment: parsed.data.comment });
      setSavedText(parsed.data.comment);
      setText(parsed.data.comment);
      notify.success(`Comment saved for ${row.studentName}`);
    } catch (cause) {
      setError(toApiError(cause).message);
    }
  };

  return (
    <Paper component="li" variant="outlined" sx={{ p: 1.5 }}>
      <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{row.studentName}</Typography>
      <Typography variant="caption" color="text.secondary" component="p" sx={{ mb: 1.5 }}>
        {row.admissionNumber}
        {row.rollNumber === null ? '' : ` · Roll ${row.rollNumber}`}
      </Typography>
      {editable ? (
        <Stack spacing={1} sx={{ alignItems: 'flex-start' }}>
          <TextField
            label="Comment"
            value={text}
            onChange={(event) => setText(event.target.value)}
            multiline
            minRows={2}
            fullWidth
            error={Boolean(error)}
            helperText={error ?? `${text.length} / ${MAX_COMMENT}`}
            slotProps={{
              htmlInput: { 'aria-label': `Comment for ${row.studentName}`, maxLength: MAX_COMMENT },
            }}
          />
          <Button
            variant="outlined"
            disabled={!dirty}
            loading={save.isPending}
            onClick={() => void submit()}
            aria-label={`Save comment for ${row.studentName}`}
          >
            Save comment
          </Button>
        </Stack>
      ) : (
        <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
          {row.comment ?? 'No comment'}
        </Typography>
      )}
    </Paper>
  );
}

/** The class teacher's comment for each student; it is printed on the report card. */
export function TeacherCommentsPage() {
  const { examId = '', sectionId = '' } = useParams();
  const sheet = useCommentSheet(examId, sectionId);

  const back = (
    <Button
      component={RouterLink}
      to="/teacher/marks"
      startIcon={<ArrowBackIcon />}
      sx={{ mb: 1, alignSelf: 'flex-start' }}
    >
      Exams and marks
    </Button>
  );

  if (sheet.isPending) {
    return (
      <>
        {back}
        <LoadingState label="Loading comments" />
      </>
    );
  }
  if (sheet.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the comments"
          error={sheet.error}
          onRetry={() => void sheet.refetch()}
        />
      </>
    );
  }

  const data = sheet.data;
  return (
    <>
      {back}
      <PageHeader
        title={`Comments · ${data.sectionName}`}
        subtitle={data.examName}
        actions={<ExamStatusChip status={data.status} />}
      />
      {!data.editable ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Comments are read-only now.
        </Alert>
      ) : null}
      {data.rows.length === 0 ? (
        <Typography color="text.secondary">No students are enrolled in this class.</Typography>
      ) : (
        <Box
          component="ul"
          aria-label="Student comments"
          sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 1.5, maxWidth: 760 }}
        >
          {data.rows.map((row) => (
            <CommentEditor
              key={`${data.examId}-${row.studentId}`}
              examId={data.examId}
              row={row}
              editable={data.editable}
            />
          ))}
        </Box>
      )}
    </>
  );
}
