import { StatusChip } from '../../../components/StatusChip';
import type { ExamStatus, ResultOutcome, SubjectStatus } from '../../../types/exams';
import {
  EXAM_STATUS_LABELS,
  RESULT_LABELS,
  SUBJECT_STATUS_LABELS,
  examTone,
  outcomeTone,
} from '../labels';

export const ExamStatusChip = ({ status }: { status: ExamStatus }) => (
  <StatusChip label={EXAM_STATUS_LABELS[status]} tone={examTone(status)} />
);

export const ResultChip = ({ result }: { result: ResultOutcome }) => (
  <StatusChip label={RESULT_LABELS[result]} tone={outcomeTone(result)} />
);

export const SubjectStatusChip = ({ status }: { status: SubjectStatus }) => (
  <StatusChip label={SUBJECT_STATUS_LABELS[status]} tone={outcomeTone(status)} />
);
