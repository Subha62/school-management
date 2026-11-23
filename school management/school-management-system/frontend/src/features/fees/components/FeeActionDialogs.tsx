import type { StudentFee } from '../../../types/fees';
import { useUnwaiveFee, useWaiveFee } from '../hooks';
import { formatMoney } from '../labels';
import { ReasonDialog } from './ReasonDialog';
import { RecordPaymentDialog } from './RecordPaymentDialog';

export interface FeeAction {
  type: 'pay' | 'waive' | 'unwaive';
  fee: StudentFee;
}

/** The dialog for one action on a fee; mount it only while the action is open. */
export function FeeActionDialog({ action, onClose }: { action: FeeAction; onClose: () => void }) {
  const waive = useWaiveFee();
  const unwaive = useUnwaiveFee();
  const { fee } = action;

  if (action.type === 'pay') {
    return <RecordPaymentDialog fee={fee} onClose={onClose} />;
  }
  if (action.type === 'waive') {
    return (
      <ReasonDialog
        title="Waive fee"
        description={`${fee.studentName} will not have to pay the remaining ${formatMoney(fee.balance)} of ${fee.categoryName}.`}
        submitLabel="Waive fee"
        successMessage="Fee waived"
        onSubmit={(reason) => waive.mutateAsync({ id: fee.id, reason })}
        onClose={onClose}
      />
    );
  }
  return (
    <ReasonDialog
      title="Remove waiver"
      description={`${fee.categoryName} for ${fee.studentName} is payable again.`}
      submitLabel="Remove waiver"
      successMessage="Waiver removed"
      onSubmit={(reason) => unwaive.mutateAsync({ id: fee.id, reason })}
      onClose={onClose}
    />
  );
}
