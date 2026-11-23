import { StatusChip } from '../../../components/StatusChip';
import type { AttendanceStatus } from '../../../types/operations';
import { STATUS_LABELS, statusTone } from '../labels';

export function AttendanceStatusChip({ status }: { status: AttendanceStatus }) {
  return <StatusChip label={STATUS_LABELS[status]} tone={statusTone(status)} />;
}
