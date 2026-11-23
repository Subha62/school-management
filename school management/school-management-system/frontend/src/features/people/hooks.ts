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
  AccountStatus,
  AdminUser,
  EmploymentStatus,
  Enrollment,
  EnrollmentStatus,
  Guardian,
  PromotionBatch,
  RoleName,
  Student,
  StudentGuardianLink,
  StudentStatus,
  Teacher,
} from '../../types/people';
import * as api from './api';

/** Every people query shares this root, so one change refreshes lists, details and pickers together. */
const ROOT = 'people';

function usePagedQuery<T>(key: readonly unknown[], fetcher: () => Promise<PageResponse<T>>) {
  return useQuery<PageResponse<T>, ApiError>({
    queryKey: [ROOT, ...key],
    queryFn: fetcher,
    placeholderData: keepPreviousData,
  });
}

export const useUsers = (params: PageQuery & { role?: RoleName; status?: AccountStatus }) =>
  usePagedQuery<AdminUser>(['users', params], () => api.listUsers(params));

export const useStudents = (params: PageQuery & { status?: StudentStatus }) =>
  usePagedQuery<Student>(['students', params], () => api.listStudents(params));

export const useStudent = (id: string) =>
  useQuery<Student, ApiError>({
    queryKey: [ROOT, 'student', id],
    queryFn: () => api.getStudent(id),
  });

export const useStudentEnrollments = (id: string) =>
  useQuery<Enrollment[], ApiError>({
    queryKey: [ROOT, 'student-enrollments', id],
    queryFn: () => api.listStudentEnrollments(id),
  });

export const useStudentGuardians = (id: string) =>
  useQuery<StudentGuardianLink[], ApiError>({
    queryKey: [ROOT, 'student-guardians', id],
    queryFn: () => api.listStudentGuardians(id),
  });

export const useTeachers = (params: PageQuery & { status?: EmploymentStatus }) =>
  usePagedQuery<Teacher>(['teachers', params], () => api.listTeachers(params));

export const useGuardians = (params: PageQuery) =>
  usePagedQuery<Guardian>(['guardians', params], () => api.listGuardians(params));

export const useEnrollments = (
  params: PageQuery & {
    academicYearId?: string;
    gradeLevelId?: string;
    sectionId?: string;
    status?: EnrollmentStatus;
  },
  enabled = true,
) =>
  useQuery<PageResponse<Enrollment>, ApiError>({
    queryKey: [ROOT, 'enrollments', params],
    queryFn: () => api.listEnrollments(params),
    placeholderData: keepPreviousData,
    enabled,
  });

export const usePromotions = (params: PageQuery) =>
  usePagedQuery<PromotionBatch>(['promotions', params], () => api.listPromotions(params));

/** Searches students while typing, for pickers. */
export const useStudentOptions = (search: string) =>
  useQuery<PageResponse<Student>, ApiError>({
    queryKey: [ROOT, 'student-options', search],
    queryFn: () =>
      api.listStudents({ page: 0, size: 20, search: search || undefined, status: 'ACTIVE' }),
    placeholderData: keepPreviousData,
  });

export const useGuardianOptions = (search: string) =>
  useQuery<PageResponse<Guardian>, ApiError>({
    queryKey: [ROOT, 'guardian-options', search],
    queryFn: () => api.listGuardians({ page: 0, size: 20, search: search || undefined }),
    placeholderData: keepPreviousData,
  });

/** Option sources for `ChoiceAutocomplete`. */
export function useStudentChoices(search: string) {
  const query = useStudentOptions(search);
  return {
    choices: (query.data?.content ?? []).map((student) => ({
      id: student.id,
      label: `${student.firstName} ${student.lastName} (${student.admissionNumber})`,
    })),
    isFetching: query.isFetching,
    isError: query.isError,
  };
}

export function useGuardianChoices(search: string) {
  const query = useGuardianOptions(search);
  return {
    choices: (query.data?.content ?? []).map((guardian) => ({
      id: guardian.id,
      label: `${guardian.firstName} ${guardian.lastName} (${guardian.phoneNumber})`,
    })),
    isFetching: query.isFetching,
    isError: query.isError,
  };
}

// ---- mutations ----
function usePeopleMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: async () => {
      // Teachers appear in the academic pickers (class teacher, assignments), so those refresh too.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [ROOT] }),
        queryClient.invalidateQueries({ queryKey: ['academics'] }),
      ]);
    },
  });
}

interface SaveVars<I> {
  id?: string;
  body: I;
}

export const useCreateAdmin = () =>
  usePeopleMutation((body: Parameters<typeof api.createAdmin>[0]) => api.createAdmin(body));
export const useChangeUserStatus = () =>
  usePeopleMutation((vars: { id: string; status: AccountStatus; version: number }) =>
    api.changeUserStatus(vars.id, vars.status, vars.version),
  );
export const useUnlockUser = () => usePeopleMutation((id: string) => api.unlockUser(id));

export const useSaveStudent = () =>
  usePeopleMutation(({ id, body }: SaveVars<Parameters<typeof api.saveStudent>[1]>) =>
    api.saveStudent(id, body),
  );
export const useSaveTeacher = () =>
  usePeopleMutation(({ id, body }: SaveVars<Parameters<typeof api.saveTeacher>[1]>) =>
    api.saveTeacher(id, body),
  );
export const useSaveGuardian = () =>
  usePeopleMutation(({ id, body }: SaveVars<Parameters<typeof api.saveGuardian>[1]>) =>
    api.saveGuardian(id, body),
  );
export const useDeleteGuardian = () => usePeopleMutation((id: string) => api.deleteGuardian(id));

export const useSaveGuardianLink = () =>
  usePeopleMutation(
    (vars: {
      studentId: string;
      linkId?: string;
      body: Parameters<typeof api.saveGuardianLink>[2];
    }) => api.saveGuardianLink(vars.studentId, vars.linkId, vars.body),
  );
export const useUnlinkGuardian = () =>
  usePeopleMutation((vars: { studentId: string; linkId: string }) =>
    api.unlinkGuardian(vars.studentId, vars.linkId),
  );

export const useEnrollStudent = () =>
  usePeopleMutation((body: Parameters<typeof api.enrollStudent>[0]) => api.enrollStudent(body));
export const useTransferEnrollment = () =>
  usePeopleMutation((vars: { id: string; body: Parameters<typeof api.transferEnrollment>[1] }) =>
    api.transferEnrollment(vars.id, vars.body),
  );
export const useWithdrawEnrollment = () =>
  usePeopleMutation((id: string) => api.withdrawEnrollment(id));
export const useCompleteEnrollment = () =>
  usePeopleMutation((id: string) => api.completeEnrollment(id));
export const usePromoteStudents = () =>
  usePeopleMutation((body: Parameters<typeof api.promoteStudents>[0]) => api.promoteStudents(body));
