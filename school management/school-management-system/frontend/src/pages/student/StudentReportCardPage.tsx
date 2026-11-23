import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { ReportCardView } from '../../features/exams/components/ReportCardView';
import { useMyReportCard } from '../../features/exams/hooks';

/** The printable report card; reached from both "Exams and results" and "Report cards". */
export function StudentReportCardPage() {
  const { examId = '' } = useParams();
  const { pathname } = useLocation();
  const card = useMyReportCard(examId);
  const fromReportCards = pathname.startsWith('/student/report-cards');
  const backTo = fromReportCards ? '/student/report-cards' : '/student/results';
  const backLabel = fromReportCards ? 'Report cards' : 'Exams and results';
  const back = (
    <Button component={RouterLink} to={backTo} startIcon={<ArrowBackIcon />}>
      {backLabel}
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
  return <ReportCardView card={card.data} actions={back} />;
}
