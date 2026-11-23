import type { AssignmentStatus, SubmissionStatus } from '../../types/assignments';

export const ASSIGNMENT_STATUS_LABELS: Record<AssignmentStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  CLOSED: 'Closed',
  ARCHIVED: 'Archived',
};

export const SUBMISSION_STATUS_LABELS: Record<SubmissionStatus, string> = {
  SUBMITTED: 'Submitted',
  LATE: 'Late',
  REVIEWED: 'Reviewed',
  RESUBMISSION_REQUESTED: 'Resubmission requested',
};

export const assignmentTone = (status: AssignmentStatus) =>
  status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'default';

export const submissionTone = (status: SubmissionStatus) =>
  status === 'REVIEWED'
    ? 'success'
    : status === 'LATE' || status === 'RESUBMISSION_REQUESTED'
      ? 'warning'
      : 'info';

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const dateTime = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export const formatDateTime = (iso: string): string => dateTime.format(new Date(iso));

const pad = (value: number) => String(value).padStart(2, '0');

/** An instant as the value of a `datetime-local` input, in the browser's time zone. */
export function toLocalInput(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** The value of a `datetime-local` input as an instant (the browser reads it in its own time zone). */
export const fromLocalInput = (value: string): string => new Date(value).toISOString();

export const formatMarks = (marks: number | null, max: number): string =>
  marks === null ? `- / ${max}` : `${marks} / ${max}`;

/** Mirrors the server's allow-list; the server remains the authority. */
export const ALLOWED_EXTENSIONS = [
  'pdf',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'txt',
  'png',
  'jpg',
  'jpeg',
] as const;
export const MAX_FILE_BYTES = 10 * 1024 * 1024;

/** Why a file would be refused by the server, found early so the user does not wait for an upload to fail. */
export function fileProblem(file: File): string | null {
  const dot = file.name.lastIndexOf('.');
  const extension = dot < 0 ? '' : file.name.slice(dot + 1).toLowerCase();
  if (!(ALLOWED_EXTENSIONS as readonly string[]).includes(extension)) {
    return `"${file.name}" is not an allowed type (${ALLOWED_EXTENSIONS.join(', ')})`;
  }
  if (file.size === 0) return `"${file.name}" is empty`;
  if (file.size > MAX_FILE_BYTES) return `"${file.name}" is larger than 10 MB`;
  return null;
}
