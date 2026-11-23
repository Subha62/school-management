import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { MarksGrid } from '../../features/exams/components/MarksGrid';
import { ExamStatusChip } from '../../features/exams/components/StatusChips';
import { useMarksSheet, useSaveMarks } from '../../features/exams/hooks';
import { formatNumber } from '../../features/exams/labels';

export function TeacherMarksEntryPage() {
  const { examId = '', examSubjectId = '', sectionId = '' } = useParams();
  const sheet = useMarksSheet(examId, examSubjectId, sectionId);
  const save = useSaveMarks();

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
        <LoadingState label="Loading marks" />
      </>
    );
  }
  if (sheet.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the marks"
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
        title={`${data.subjectName} · ${data.sectionName}`}
        subtitle={data.examName}
        actions={<ExamStatusChip status={data.status} />}
      />
      <Stack direction="row" spacing={3} useFlexGap sx={{ mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="body2">
          <strong>Maximum marks:</strong> {formatNumber(data.maxMarks)}
        </Typography>
        <Typography variant="body2">
          <strong>Pass marks:</strong> {formatNumber(data.passMarks)}
        </Typography>
        <Typography variant="body2">
          <strong>Students:</strong> {data.rows.length}
        </Typography>
      </Stack>
      <MarksGrid
        key={`${examId}/${examSubjectId}/${sectionId}`}
        sheet={data}
        saving={save.isPending}
        onSave={(entries) =>
          save.mutateAsync({ examId, examSubjectId, sectionId, body: { entries } })
        }
      />
    </>
  );
}
