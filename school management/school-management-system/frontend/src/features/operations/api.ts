import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { PageQuery } from '../../types/academics';
import type {
  AttendanceDetail,
  AttendanceReport,
  AttendanceSummary,
  ClassOption,
  CorrectionInput,
  CorrectionRecord,
  DayName,
  MarkInput,
  OpenSessionInput,
  SessionStatus,
  SessionType,
  StudentMonth,
  TimetableEntry,
  TimetableInput,
  TimetableWeek,
} from '../../types/operations';

const ADMIN = '/admin';
const TEACHER = '/teacher';
const STUDENT = '/student';

async function get<T>(path: string, params?: object): Promise<T> {
  const { data } = await apiClient.get<T>(path, { params });
  return data;
}

async function post<T>(path: string, body?: object): Promise<T> {
  const { data } = await apiClient.post<T>(path, body);
  return data;
}

// ---- timetable (administrators) ----
export const listTimetableEntries = (
  params: PageQuery & {
    academicYearId?: string;
    sectionId?: string;
    teacherId?: string;
    day?: DayName;
  },
) => get<PageResponse<TimetableEntry>>(`${ADMIN}/timetable-entries`, params);

export async function saveTimetableEntry(
  id: string | undefined,
  body: TimetableInput,
): Promise<TimetableEntry> {
  const { data } = id
    ? await apiClient.put<TimetableEntry>(`${ADMIN}/timetable-entries/${id}`, body)
    : await apiClient.post<TimetableEntry>(`${ADMIN}/timetable-entries`, body);
  return data;
}

export const deleteTimetableEntry = async (id: string): Promise<void> => {
  await apiClient.delete(`${ADMIN}/timetable-entries/${id}`);
};

export const getSectionWeek = (sectionId: string, date: string) =>
  get<TimetableWeek>(`${ADMIN}/timetable/sections/${sectionId}`, { date });
export const getTeacherWeekForAdmin = (teacherId: string, date: string) =>
  get<TimetableWeek>(`${ADMIN}/timetable/teachers/${teacherId}`, { date });

// ---- timetable (teachers and students: always their own) ----
export const getMyTeacherWeek = (date: string) =>
  get<TimetableWeek>(`${TEACHER}/timetable`, { date });
export const getMyStudentWeek = (date: string) =>
  get<TimetableWeek>(`${STUDENT}/timetable`, { date });

// ---- attendance (teachers) ----
export const listMyClasses = () => get<ClassOption[]>(`${TEACHER}/attendance/classes`);
export const listMySessions = (params: PageQuery & { status?: SessionStatus }) =>
  get<PageResponse<AttendanceSummary>>(`${TEACHER}/attendance/sessions`, params);
export const openSession = (body: OpenSessionInput) =>
  post<AttendanceDetail>(`${TEACHER}/attendance/sessions`, body);
export const getMySession = (id: string) =>
  get<AttendanceDetail>(`${TEACHER}/attendance/sessions/${id}`);
export async function markAttendance(id: string, records: MarkInput[]): Promise<AttendanceDetail> {
  const { data } = await apiClient.put<AttendanceDetail>(
    `${TEACHER}/attendance/sessions/${id}/records`,
    { records },
  );
  return data;
}
export const finalizeSession = (id: string) =>
  post<AttendanceDetail>(`${TEACHER}/attendance/sessions/${id}/finalize`);
export const deleteSession = async (id: string): Promise<void> => {
  await apiClient.delete(`${TEACHER}/attendance/sessions/${id}`);
};
export const getTeacherMonthly = (params: {
  sectionId: string;
  month: string;
  type: SessionType;
  subjectId?: string;
}) => get<AttendanceReport>(`${TEACHER}/attendance/reports/monthly`, params);

// ---- attendance (administrators) ----
export const listSessions = (
  params: PageQuery & {
    sectionId?: string;
    from?: string;
    to?: string;
    type?: SessionType;
    status?: SessionStatus;
  },
) => get<PageResponse<AttendanceSummary>>(`${ADMIN}/attendance/sessions`, params);
export const getAdminSession = (id: string) =>
  get<AttendanceDetail>(`${ADMIN}/attendance/sessions/${id}`);
export const listCorrections = (id: string) =>
  get<CorrectionRecord[]>(`${ADMIN}/attendance/sessions/${id}/corrections`);
export const correctAttendance = (id: string, body: CorrectionInput) =>
  post<AttendanceDetail>(`${ADMIN}/attendance/sessions/${id}/corrections`, body);
export const getAdminMonthly = (params: {
  sectionId: string;
  month: string;
  type: SessionType;
  subjectId?: string;
}) => get<AttendanceReport>(`${ADMIN}/attendance/reports/monthly`, params);
export const getBelowThreshold = (params: {
  from?: string;
  to?: string;
  sectionId?: string;
  type: SessionType;
  subjectId?: string;
  threshold?: number;
}) => get<AttendanceReport>(`${ADMIN}/attendance/reports/below-threshold`, params);

// ---- attendance (students) ----
export const getMyAttendance = (month: string) =>
  get<StudentMonth>(`${STUDENT}/attendance`, { month });
