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
  AttendanceDetail,
  AttendanceReport,
  AttendanceSummary,
  ClassOption,
  CorrectionRecord,
  DayName,
  SessionStatus,
  SessionType,
  StudentMonth,
  TimetableEntry,
  TimetableWeek,
} from '../../types/operations';
import * as api from './api';

/** Every timetable and attendance query shares this root, so one change refreshes every related view. */
const ROOT = 'operations';

// ---- timetable ----
export const useTimetableEntries = (
  params: PageQuery & {
    academicYearId?: string;
    sectionId?: string;
    teacherId?: string;
    day?: DayName;
  },
  enabled = true,
) =>
  useQuery<PageResponse<TimetableEntry>, ApiError>({
    queryKey: [ROOT, 'entries', params],
    queryFn: () => api.listTimetableEntries(params),
    placeholderData: keepPreviousData,
    enabled,
  });

export const useSectionWeek = (sectionId: string, date: string) =>
  useQuery<TimetableWeek, ApiError>({
    queryKey: [ROOT, 'week', 'section', sectionId, date],
    queryFn: () => api.getSectionWeek(sectionId, date),
    enabled: Boolean(sectionId),
    placeholderData: keepPreviousData,
  });

export const useTeacherWeekForAdmin = (teacherId: string, date: string) =>
  useQuery<TimetableWeek, ApiError>({
    queryKey: [ROOT, 'week', 'teacher', teacherId, date],
    queryFn: () => api.getTeacherWeekForAdmin(teacherId, date),
    enabled: Boolean(teacherId),
    placeholderData: keepPreviousData,
  });

export const useMyTeacherWeek = (date: string) =>
  useQuery<TimetableWeek, ApiError>({
    queryKey: [ROOT, 'week', 'me-teacher', date],
    queryFn: () => api.getMyTeacherWeek(date),
    placeholderData: keepPreviousData,
  });

export const useMyStudentWeek = (date: string) =>
  useQuery<TimetableWeek, ApiError>({
    queryKey: [ROOT, 'week', 'me-student', date],
    queryFn: () => api.getMyStudentWeek(date),
    placeholderData: keepPreviousData,
  });

// ---- attendance ----
export const useMyClasses = (enabled = true) =>
  useQuery<ClassOption[], ApiError>({
    queryKey: [ROOT, 'my-classes'],
    queryFn: api.listMyClasses,
    enabled,
  });

export const useMySessions = (params: PageQuery & { status?: SessionStatus }) =>
  useQuery<PageResponse<AttendanceSummary>, ApiError>({
    queryKey: [ROOT, 'my-sessions', params],
    queryFn: () => api.listMySessions(params),
    placeholderData: keepPreviousData,
  });

export const useMySession = (id: string | null) =>
  useQuery<AttendanceDetail, ApiError>({
    queryKey: [ROOT, 'my-session', id],
    queryFn: () => api.getMySession(id ?? ''),
    enabled: id !== null,
  });

export const useAdminSessions = (
  params: PageQuery & {
    sectionId?: string;
    from?: string;
    to?: string;
    type?: SessionType;
    status?: SessionStatus;
  },
) =>
  useQuery<PageResponse<AttendanceSummary>, ApiError>({
    queryKey: [ROOT, 'admin-sessions', params],
    queryFn: () => api.listSessions(params),
    placeholderData: keepPreviousData,
  });

export const useAdminSession = (id: string) =>
  useQuery<AttendanceDetail, ApiError>({
    queryKey: [ROOT, 'admin-session', id],
    queryFn: () => api.getAdminSession(id),
  });

export const useCorrections = (id: string) =>
  useQuery<CorrectionRecord[], ApiError>({
    queryKey: [ROOT, 'corrections', id],
    queryFn: () => api.listCorrections(id),
  });

interface MonthlyParams {
  sectionId: string;
  month: string;
  type: SessionType;
  subjectId?: string;
}

export const useAdminMonthly = (params: MonthlyParams) =>
  useQuery<AttendanceReport, ApiError>({
    queryKey: [ROOT, 'monthly', 'admin', params],
    queryFn: () => api.getAdminMonthly(params),
    enabled: Boolean(params.sectionId) && (params.type === 'DAILY' || Boolean(params.subjectId)),
    placeholderData: keepPreviousData,
  });

export const useTeacherMonthly = (params: MonthlyParams) =>
  useQuery<AttendanceReport, ApiError>({
    queryKey: [ROOT, 'monthly', 'teacher', params],
    queryFn: () => api.getTeacherMonthly(params),
    enabled: Boolean(params.sectionId) && (params.type === 'DAILY' || Boolean(params.subjectId)),
    placeholderData: keepPreviousData,
  });

export const useBelowThreshold = (params: Parameters<typeof api.getBelowThreshold>[0]) =>
  useQuery<AttendanceReport, ApiError>({
    queryKey: [ROOT, 'below-threshold', params],
    queryFn: () => api.getBelowThreshold(params),
    enabled: params.type === 'DAILY' || Boolean(params.subjectId),
    placeholderData: keepPreviousData,
  });

export const useMyAttendance = (month: string) =>
  useQuery<StudentMonth, ApiError>({
    queryKey: [ROOT, 'my-attendance', month],
    queryFn: () => api.getMyAttendance(month),
    placeholderData: keepPreviousData,
  });

// ---- mutations ----
function useOperationsMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export const useSaveTimetableEntry = () =>
  useOperationsMutation(
    (vars: { id?: string; body: Parameters<typeof api.saveTimetableEntry>[1] }) =>
      api.saveTimetableEntry(vars.id, vars.body),
  );
export const useDeleteTimetableEntry = () =>
  useOperationsMutation((id: string) => api.deleteTimetableEntry(id));

export const useOpenSession = () =>
  useOperationsMutation((body: Parameters<typeof api.openSession>[0]) => api.openSession(body));
export const useMarkAttendance = () =>
  useOperationsMutation((vars: { id: string; records: Parameters<typeof api.markAttendance>[1] }) =>
    api.markAttendance(vars.id, vars.records),
  );
export const useFinalizeSession = () =>
  useOperationsMutation((id: string) => api.finalizeSession(id));
export const useDeleteSession = () => useOperationsMutation((id: string) => api.deleteSession(id));
export const useCorrectAttendance = () =>
  useOperationsMutation((vars: { id: string; body: Parameters<typeof api.correctAttendance>[1] }) =>
    api.correctAttendance(vars.id, vars.body),
  );
