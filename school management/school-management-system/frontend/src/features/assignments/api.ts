import { apiClient } from '../../api/client';
import type { PageQuery } from '../../types/academics';
import type { PageResponse } from '../../types/api';
import type {
  Assignment,
  AssignmentInput,
  AssignmentStatus,
  ReviewInput,
  RosterRow,
  StudentAssignment,
  Submission,
  SubmissionListItem,
  SubmissionStatus,
} from '../../types/assignments';

const TEACHER = '/teacher';
const STUDENT = '/student';
/** Uploads can be several megabytes on a slow connection, so they get longer than ordinary requests. */
const UPLOAD_TIMEOUT_MS = 120_000;

async function get<T>(path: string, params?: object): Promise<T> {
  const { data } = await apiClient.get<T>(path, { params });
  return data;
}

async function post<T>(path: string, body?: object): Promise<T> {
  const { data } = await apiClient.post<T>(path, body);
  return data;
}

function formOf(files: File[], fields: Record<string, string | null> = {}): FormData {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    if (value !== null) form.append(name, value);
  }
  files.forEach((file) => form.append('files', file));
  return form;
}

// ---- teacher ----
export const listAssignments = (
  params: PageQuery & { sectionId?: string; status?: AssignmentStatus },
) => get<PageResponse<Assignment>>(`${TEACHER}/assignments`, params);
export const getAssignment = (id: string) => get<Assignment>(`${TEACHER}/assignments/${id}`);

export async function saveAssignment(
  id: string | undefined,
  body: AssignmentInput,
): Promise<Assignment> {
  const { data } = id
    ? await apiClient.put<Assignment>(`${TEACHER}/assignments/${id}`, body)
    : await apiClient.post<Assignment>(`${TEACHER}/assignments`, body);
  return data;
}

export const deleteAssignment = async (id: string): Promise<void> => {
  await apiClient.delete(`${TEACHER}/assignments/${id}`);
};
export const transitionAssignment = (
  id: string,
  action: 'publish' | 'close' | 'reopen' | 'archive',
) => post<Assignment>(`${TEACHER}/assignments/${id}/${action}`);

export async function addAssignmentFiles(id: string, files: File[]): Promise<Assignment> {
  const { data } = await apiClient.post<Assignment>(
    `${TEACHER}/assignments/${id}/attachments`,
    formOf(files),
    { timeout: UPLOAD_TIMEOUT_MS },
  );
  return data;
}

export async function removeAssignmentFile(id: string, attachmentId: string): Promise<Assignment> {
  const { data } = await apiClient.delete<Assignment>(
    `${TEACHER}/assignments/${id}/attachments/${attachmentId}`,
  );
  return data;
}

export const listRoster = (id: string) =>
  get<RosterRow[]>(`${TEACHER}/assignments/${id}/submissions`);
export const getSubmission = (assignmentId: string, submissionId: string) =>
  get<Submission>(`${TEACHER}/assignments/${assignmentId}/submissions/${submissionId}`);

export async function reviewSubmission(
  assignmentId: string,
  submissionId: string,
  body: ReviewInput,
): Promise<Submission> {
  const { data } = await apiClient.put<Submission>(
    `${TEACHER}/assignments/${assignmentId}/submissions/${submissionId}/review`,
    body,
  );
  return data;
}

export const listReviewQueue = (
  params: PageQuery & { status?: SubmissionStatus; pending?: boolean; assignmentId?: string },
) => get<PageResponse<SubmissionListItem>>(`${TEACHER}/submissions`, params);

// ---- student ----
export const listStudentAssignments = (
  params: PageQuery & { status?: Extract<AssignmentStatus, 'PUBLISHED' | 'CLOSED'> },
) => get<PageResponse<StudentAssignment>>(`${STUDENT}/assignments`, params);
export const getStudentAssignment = (id: string) =>
  get<StudentAssignment>(`${STUDENT}/assignments/${id}`);

export async function submitWork(
  id: string,
  textAnswer: string,
  files: File[],
): Promise<StudentAssignment> {
  const { data } = await apiClient.post<StudentAssignment>(
    `${STUDENT}/assignments/${id}/submission`,
    formOf(files, { textAnswer: textAnswer.trim() === '' ? null : textAnswer }),
    { timeout: UPLOAD_TIMEOUT_MS },
  );
  return data;
}

export async function removeSubmissionFile(
  id: string,
  attachmentId: string,
): Promise<StudentAssignment> {
  const { data } = await apiClient.delete<StudentAssignment>(
    `${STUDENT}/assignments/${id}/submission/attachments/${attachmentId}`,
  );
  return data;
}

export const listMySubmissions = (params: PageQuery) =>
  get<PageResponse<SubmissionListItem>>(`${STUDENT}/submissions`, params);

// ---- downloads ----
export const teacherAssignmentFilePath = (id: string, attachmentId: string) =>
  `${TEACHER}/assignments/${id}/attachments/${attachmentId}/download`;
export const teacherSubmissionFilePath = (id: string, submissionId: string, attachmentId: string) =>
  `${TEACHER}/assignments/${id}/submissions/${submissionId}/attachments/${attachmentId}/download`;
export const studentAssignmentFilePath = (id: string, attachmentId: string) =>
  `${STUDENT}/assignments/${id}/attachments/${attachmentId}/download`;
export const studentOwnFilePath = (id: string, attachmentId: string) =>
  `${STUDENT}/assignments/${id}/submission/attachments/${attachmentId}/download`;

/** Fetches a file through the API client (so cookies, CSRF and session refresh apply) and saves it. */
export async function downloadFile(path: string, fileName: string): Promise<void> {
  const { data } = await apiClient.get<Blob>(path, {
    responseType: 'blob',
    timeout: UPLOAD_TIMEOUT_MS,
  });
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
