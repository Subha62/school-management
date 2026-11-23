import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { PageResponse } from '../../types/api';
import type {
  AcademicYear,
  AcademicYearStatus,
  GradeLevel,
  PageQuery,
  Section,
  SectionSubject,
  Subject,
  TeacherOption,
} from '../../types/academics';
import * as api from './api';

/** Every academic query shares this root, so one invalidation refreshes lists and dropdown options. */
const ROOT = 'academics';

const OPTIONS_PAGE = { page: 0, size: 100 } as const;

function usePagedQuery<T>(key: readonly unknown[], fetcher: () => Promise<PageResponse<T>>) {
  return useQuery<PageResponse<T>, ApiError>({
    queryKey: [ROOT, ...key],
    queryFn: fetcher,
    placeholderData: keepPreviousData,
  });
}

export const useAcademicYears = (params: PageQuery & { status?: AcademicYearStatus }) =>
  usePagedQuery<AcademicYear>(['years', params], () => api.listAcademicYears(params));

export const useGrades = (params: PageQuery & { active?: boolean }) =>
  usePagedQuery<GradeLevel>(['grades', params], () => api.listGrades(params));

export const useSubjects = (params: PageQuery & { active?: boolean }) =>
  usePagedQuery<Subject>(['subjects', params], () => api.listSubjects(params));

export const useSections = (
  params: PageQuery & { academicYearId?: string; gradeLevelId?: string; active?: boolean },
) => usePagedQuery<Section>(['sections', params], () => api.listSections(params));

export const useAssignments = (
  params: PageQuery & { academicYearId?: string; sectionId?: string; teacherId?: string },
  enabled = true,
) =>
  useQuery<PageResponse<SectionSubject>, ApiError>({
    queryKey: [ROOT, 'assignments', params],
    queryFn: () => api.listAssignments(params),
    placeholderData: keepPreviousData,
    enabled,
  });

// ---- dropdown options (first 100 records) ----
export const useYearOptions = () =>
  usePagedQuery<AcademicYear>(['year-options'], () => api.listAcademicYears(OPTIONS_PAGE));

export const useGradeOptions = (active?: boolean) =>
  usePagedQuery<GradeLevel>(['grade-options', active], () =>
    api.listGrades({ ...OPTIONS_PAGE, active }),
  );

export const useSubjectOptions = (active?: boolean) =>
  usePagedQuery<Subject>(['subject-options', active], () =>
    api.listSubjects({ ...OPTIONS_PAGE, active }),
  );

export const useSectionOptions = (academicYearId: string | undefined) =>
  useQuery<PageResponse<Section>, ApiError>({
    queryKey: [ROOT, 'section-options', academicYearId],
    queryFn: () => api.listSections({ ...OPTIONS_PAGE, academicYearId }),
    enabled: Boolean(academicYearId),
  });

export const useTeacherOptions = (search: string) =>
  useQuery<PageResponse<TeacherOption>, ApiError>({
    queryKey: [ROOT, 'teacher-options', search],
    queryFn: () => api.listTeacherOptions({ page: 0, size: 20, search: search || undefined }),
    placeholderData: keepPreviousData,
  });

// ---- mutations ----
function useAcademicMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

interface SaveVars<I> {
  id?: string;
  body: I;
}

export const useSaveAcademicYear = () =>
  useAcademicMutation(({ id, body }: SaveVars<Parameters<typeof api.saveAcademicYear>[1]>) =>
    api.saveAcademicYear(id, body),
  );
export const useActivateAcademicYear = () =>
  useAcademicMutation((id: string) => api.activateAcademicYear(id));
export const useCloseAcademicYear = () =>
  useAcademicMutation((id: string) => api.closeAcademicYear(id));
export const useDeleteAcademicYear = () =>
  useAcademicMutation((id: string) => api.deleteAcademicYear(id));

export const useSaveGrade = () =>
  useAcademicMutation(({ id, body }: SaveVars<Parameters<typeof api.saveGrade>[1]>) =>
    api.saveGrade(id, body),
  );
export const useDeleteGrade = () => useAcademicMutation((id: string) => api.deleteGrade(id));

export const useSaveSubject = () =>
  useAcademicMutation(({ id, body }: SaveVars<Parameters<typeof api.saveSubject>[1]>) =>
    api.saveSubject(id, body),
  );
export const useDeleteSubject = () => useAcademicMutation((id: string) => api.deleteSubject(id));

export const useSaveSection = () =>
  useAcademicMutation(({ id, body }: SaveVars<Parameters<typeof api.saveSection>[1]>) =>
    api.saveSection(id, body),
  );
export const useDeleteSection = () => useAcademicMutation((id: string) => api.deleteSection(id));

export const useSaveAssignment = () =>
  useAcademicMutation(({ id, body }: SaveVars<Parameters<typeof api.saveAssignment>[1]>) =>
    api.saveAssignment(id, body),
  );
export const useDeleteAssignment = () =>
  useAcademicMutation((id: string) => api.deleteAssignment(id));
