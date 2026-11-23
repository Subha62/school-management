import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { FeeDetailView } from '../../features/fees/components/FeeDetailView';
import { useMyFee } from '../../features/fees/hooks';

export function StudentFeeDetailPage() {
  const { id = '' } = useParams();
  const detail = useMyFee(id);
  const back = (
    <Button component={RouterLink} to="/student/fees" startIcon={<ArrowBackIcon />}>
      Fees
    </Button>
  );

  if (detail.isPending) {
    return (
      <>
        {back}
        <LoadingState label="Loading fee" />
      </>
    );
  }
  if (detail.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the fee"
          error={detail.error}
          onRetry={() => void detail.refetch()}
        />
      </>
    );
  }
  return (
    <>
      {back}
      <PageHeader title={detail.data.fee.categoryName} subtitle="Fee details and payments" />
      <FeeDetailView
        detail={detail.data}
        receiptHref={(payment) => `/student/fees/${id}/receipts/${payment.id}`}
      />
    </>
  );
}
