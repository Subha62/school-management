import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { FeeActionDialog, type FeeAction } from '../../features/fees/components/FeeActionDialogs';
import { FeeDetailView } from '../../features/fees/components/FeeDetailView';
import { useStudentFee } from '../../features/fees/hooks';
import { canRecordPayment } from '../../features/fees/labels';

export function AdminStudentFeeDetailPage() {
  const { id = '' } = useParams();
  const detail = useStudentFee(id);
  const [action, setAction] = useState<FeeAction | null>(null);
  const back = (
    <Button component={RouterLink} to="/admin/student-fees" startIcon={<ArrowBackIcon />}>
      Student fees
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

  const { fee } = detail.data;
  return (
    <>
      {back}
      <PageHeader
        title={fee.studentName}
        subtitle={`${fee.admissionNumber} · ${fee.categoryName}`}
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {canRecordPayment(fee.status) ? (
              <Button variant="contained" onClick={() => setAction({ type: 'pay', fee })}>
                Record payment
              </Button>
            ) : null}
            {fee.status === 'WAIVED' ? (
              <Button variant="outlined" onClick={() => setAction({ type: 'unwaive', fee })}>
                Remove waiver
              </Button>
            ) : fee.status !== 'PAID' ? (
              <Button variant="outlined" onClick={() => setAction({ type: 'waive', fee })}>
                Waive
              </Button>
            ) : null}
          </Stack>
        }
      />
      <FeeDetailView
        detail={detail.data}
        showStudent
        receiptHref={(payment) => `/admin/payments/${payment.id}/receipt`}
      />
      {action ? <FeeActionDialog action={action} onClose={() => setAction(null)} /> : null}
    </>
  );
}
