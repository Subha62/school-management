import type { ExamAction, ExamStatus, ResultOutcome, SubjectStatus } from '../../types/exams';

export const EXAM_STATUS_LABELS: Record<ExamStatus, string> = {
  DRAFT: 'Draft',
  OPEN_FOR_MARKS: 'Open for marks',
  LOCKED: 'Locked',
  PUBLISHED: 'Published',
};

export const examTone = (status: ExamStatus) =>
  status === 'PUBLISHED'
    ? 'success'
    : status === 'OPEN_FOR_MARKS'
      ? 'info'
      : status === 'LOCKED'
        ? 'warning'
        : 'default';

export const RESULT_LABELS: Record<ResultOutcome, string> = { PASS: 'Pass', FAIL: 'Fail' };
export const SUBJECT_STATUS_LABELS: Record<SubjectStatus, string> = {
  PASS: 'Pass',
  FAIL: 'Fail',
  ABSENT: 'Absent',
};

export const outcomeTone = (status: SubjectStatus | ResultOutcome) =>
  status === 'PASS' ? 'success' : status === 'FAIL' ? 'warning' : 'default';

export const EXAM_STATUS_ORDER: ExamStatus[] = ['DRAFT', 'OPEN_FOR_MARKS', 'LOCKED', 'PUBLISHED'];

export const EXAM_ACTIONS: Record<
  ExamAction,
  { title: string; message: string; label: string; done: string; destructive?: boolean }
> = {
  open: {
    title: 'Open this exam for marks?',
    message:
      'Teachers can start entering marks. The subjects, maximum marks and pass marks can no longer be edited.',
    label: 'Open for marks',
    done: 'Exam opened for marks',
  },
  lock: {
    title: 'Lock the marks?',
    message:
      'Teachers can no longer change marks. Every mark must be entered and the grade scale must cover 0 to 100. You can reopen the exam until you publish.',
    label: 'Lock marks',
    done: 'Marks locked',
  },
  reopen: {
    title: 'Reopen this exam for marks?',
    message: 'Teachers can change marks again. Results stay unpublished.',
    label: 'Reopen',
    done: 'Exam reopened for marks',
  },
  publish: {
    title: 'Publish the results?',
    message:
      'Students can see their results and report cards. This cannot be undone, and marks can no longer be changed.',
    label: 'Publish results',
    done: 'Results published',
    destructive: true,
  },
};

/** Marks as shown to people: `42` rather than `42.00`, at most two decimals. */
export function formatNumber(value: number): string {
  return String(Math.round(value * 100) / 100);
}

export const formatPercent = (value: number | null): string =>
  value === null ? '-' : `${formatNumber(value)}%`;

export const formatMarks = (marks: number | null, max: number): string =>
  marks === null ? '-' : `${formatNumber(marks)} / ${formatNumber(max)}`;

const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
});

export const formatInstant = (iso: string | null): string =>
  iso ? dateTimeFormat.format(new Date(iso)) : '-';

/** "Jan 2, 2027 - Jan 9, 2027", either end may be missing. */
export function formatDateRange(
  start: string | null,
  end: string | null,
  format: (iso: string) => string,
): string {
  if (start && end) return `${format(start)} - ${format(end)}`;
  if (start) return `From ${format(start)}`;
  if (end) return `Until ${format(end)}`;
  return 'No dates set';
}
