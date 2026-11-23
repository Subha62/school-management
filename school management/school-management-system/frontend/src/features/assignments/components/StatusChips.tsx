import { StatusChip } from '../../../components/StatusChip';
import type { AssignmentStatus, SubmissionStatus } from '../../../types/assignments';
import {
  ASSIGNMENT_STATUS_LABELS,
  SUBMISSION_STATUS_LABELS,
  assignmentTone,
  submissionTone,
} from '../labels';

export function AssignmentStatusChip({ status }: { status: AssignmentStatus }) {
  return <StatusChip label={ASSIGNMENT_STATUS_LABELS[status]} tone={assignmentTone(status)} />;
}

/** A null status means the student has not handed anything in. */
export function SubmissionStatusChip({ status }: { status: SubmissionStatus | null }) {
  return status === null ? (
    <StatusChip label="Not submitted" />
  ) : (
    <StatusChip label={SUBMISSION_STATUS_LABELS[status]} tone={submissionTone(status)} />
  );
}
