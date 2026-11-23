import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { PageQuery } from '../../types/academics';
import type { PageResponse } from '../../types/api';
import type {
  Assignment,
  AssignmentStatus,
  RosterRow,
  StudentAssignment,
  Submission,
  SubmissionListItem,
  SubmissionStatus,
} from '../../types/assignments';
import * as api from './api';

/** Every assignment query shares this root, so one change refreshes lists, details and queues together. */
const ROOT = 'assignments';

export const useAssignments = (
  params: PageQuery & { sectionId?: string; status?: AssignmentStatus },
) =>
  useQuery<PageResponse<Assignment>, ApiError>({
    queryKey: [ROOT, 'teacher-list', params],
    queryFn: () => api.listAssignments(params),
    placeholderData: keepPreviousData,
  });

export const useAssignment = (id: string) =>
  useQuery<Assignment, ApiError>({
    queryKey: [ROOT, 'teacher', id],
    queryFn: () => api.getAssignment(id),
  });

export const useRoster = (id: string) =>
  useQuery<RosterRow[], ApiError>({
    queryKey: [ROOT, 'roster', id],
    queryFn: () => api.listRoster(id),
  });

export const useSubmission = (assignmentId: string, submissionId: string) =>
  useQuery<Submission, ApiError>({
    queryKey: [ROOT, 'submission', assignmentId, submissionId],
    queryFn: () => api.getSubmission(assignmentId, submissionId),
  });

export const useReviewQueue = (
  params: PageQuery & { status?: SubmissionStatus; pending?: boolean; assignmentId?: string },
) =>
  useQuery<PageResponse<SubmissionListItem>, ApiError>({
    queryKey: [ROOT, 'queue', params],
    queryFn: () => api.listReviewQueue(params),
    placeholderData: keepPreviousData,
  });

export const useStudentAssignments = (
  params: PageQuery & { status?: Extract<AssignmentStatus, 'PUBLISHED' | 'CLOSED'> },
) =>
  useQuery<PageResponse<StudentAssignment>, ApiError>({
    queryKey: [ROOT, 'student-list', params],
    queryFn: () => api.listStudentAssignments(params),
    placeholderData: keepPreviousData,
  });

export const useStudentAssignment = (id: string) =>
  useQuery<StudentAssignment, ApiError>({
    queryKey: [ROOT, 'student', id],
    queryFn: () => api.getStudentAssignment(id),
  });

export const useMySubmissions = (params: PageQuery) =>
  useQuery<PageResponse<SubmissionListItem>, ApiError>({
    queryKey: [ROOT, 'mine', params],
    queryFn: () => api.listMySubmissions(params),
    placeholderData: keepPreviousData,
  });

// ---- mutations ----
function useAssignmentMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export const useSaveAssignment = () =>
  useAssignmentMutation((vars: { id?: string; body: Parameters<typeof api.saveAssignment>[1] }) =>
    api.saveAssignment(vars.id, vars.body),
  );
export const useDeleteAssignment = () =>
  useAssignmentMutation((id: string) => api.deleteAssignment(id));
export const useTransitionAssignment = () =>
  useAssignmentMutation(
    (vars: { id: string; action: Parameters<typeof api.transitionAssignment>[1] }) =>
      api.transitionAssignment(vars.id, vars.action),
  );
export const useAddAssignmentFiles = () =>
  useAssignmentMutation((vars: { id: string; files: File[] }) =>
    api.addAssignmentFiles(vars.id, vars.files),
  );
export const useRemoveAssignmentFile = () =>
  useAssignmentMutation((vars: { id: string; attachmentId: string }) =>
    api.removeAssignmentFile(vars.id, vars.attachmentId),
  );
export const useReviewSubmission = () =>
  useAssignmentMutation(
    (vars: {
      assignmentId: string;
      submissionId: string;
      body: Parameters<typeof api.reviewSubmission>[2];
    }) => api.reviewSubmission(vars.assignmentId, vars.submissionId, vars.body),
  );
export const useSubmitWork = () =>
  useAssignmentMutation((vars: { id: string; textAnswer: string; files: File[] }) =>
    api.submitWork(vars.id, vars.textAnswer, vars.files),
  );
export const useRemoveSubmissionFile = () =>
  useAssignmentMutation((vars: { id: string; attachmentId: string }) =>
    api.removeSubmissionFile(vars.id, vars.attachmentId),
  );
