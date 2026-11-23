import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { ReceiptView } from '../../features/fees/components/ReceiptView';
import { useMyReceipt } from '../../features/fees/hooks';

/** The printable receipt of one of the student's own payments. */
export function StudentReceiptPage() {
  const { id = '', paymentId = '' } = useParams();
  const receipt = useMyReceipt(paymentId);
  const back = (
    <Button component={RouterLink} to={`/student/fees/${id}`} startIcon={<ArrowBackIcon />}>
      Fee details
    </Button>
  );

  if (receipt.isPending) {
    return (
      <>
        {back}
        <LoadingState label="Loading receipt" />
      </>
    );
  }
  if (receipt.error) {
    return (
      <>
        {back}
        <ErrorState
          title="Unable to load the receipt"
          error={receipt.error}
          onRetry={() => void receipt.refetch()}
        />
      </>
    );
  }
  return <ReceiptView receipt={receipt.data} actions={back} />;
}
