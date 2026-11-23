import { StatusChip } from '../../../components/StatusChip';
import type { FeeStatus } from '../../../types/fees';
import { FEE_STATUS_LABELS, feeTone } from '../labels';

export function FeeStatusChip({ status }: { status: FeeStatus }) {
  return <StatusChip label={FEE_STATUS_LABELS[status]} tone={feeTone(status)} />;
}
