import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { ReportCardView } from '../../features/exams/components/ReportCardView';
import { useStudentReportCard } from '../../features/exams/hooks';

export function AdminReportCardPage() {
  const { examId = '', studentId = '' } = useParams();
  const card = useStudentReportCard(examId, studentId);
  const backTo = (sectionId?: string) =>
    `/admin/results?examId=${examId}${sectionId ? `&sectionId=${sectionId}` : ''}`;
  const back = (
    <Button
      component={RouterLink}
      to={backTo()}
      startIcon={<ArrowBackIcon />}
      className="no-print"
      sx={{ mb: 1 }}
    >
      Results
    </Button>
  );

  if (card.isPending) {
    return (
      <>
        {back}
        <LoadingState label="Loading report card" />
      </>
    );
  }
  if (card.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the report card"
          error={card.error}
          onRetry={() => void card.refetch()}
        />
      </>
    );
  }
  return (
    <ReportCardView
      card={card.data}
      preview={card.data.publishedAt === null}
      actions={
        <Button
          component={RouterLink}
          to={backTo(card.data.result.sectionId)}
          startIcon={<ArrowBackIcon />}
        >
          Results
        </Button>
      }
    />
  );
}
