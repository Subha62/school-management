import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { PageQuery } from '../../types/academics';
import type {
  AccountStatus,
  AdminInput,
  AdminUser,
  EmploymentStatus,
  EnrollInput,
  Enrollment,
  EnrollmentStatus,
  Guardian,
  GuardianInput,
  GuardianLinkInput,
  PromotionBatch,
  PromotionInput,
  PromotionSummary,
  RoleName,
  Student,
  StudentGuardianLink,
  StudentInput,
  StudentStatus,
  Teacher,
  TeacherInput,
  TransferInput,
} from '../../types/people';

const BASE = '/admin';

async function list<T>(path: string, params: object): Promise<PageResponse<T>> {
  const { data } = await apiClient.get<PageResponse<T>>(`${BASE}/${path}`, { params });
  return data;
}

async function save<T, I extends { version?: number }>(
  path: string,
  id: string | undefined,
  body: I,
): Promise<T> {
  const { data } = id
    ? await apiClient.put<T>(`${BASE}/${path}/${id}`, body)
    : await apiClient.post<T>(`${BASE}/${path}`, body);
  return data;
}

async function post<T>(path: string, body?: object): Promise<T> {
  const { data } = await apiClient.post<T>(`${BASE}/${path}`, body);
  return data;
}

async function get<T>(path: string): Promise<T> {
  const { data } = await apiClient.get<T>(`${BASE}/${path}`);
  return data;
}

// ---- users ----
export const listUsers = (params: PageQuery & { role?: RoleName; status?: AccountStatus }) =>
  list<AdminUser>('users', params);
export const createAdmin = (body: AdminInput) => post<AdminUser>('users/admins', body);
export const changeUserStatus = (id: string, status: AccountStatus, version: number) =>
  post<AdminUser>(`users/${id}/status`, { status, version });
export const unlockUser = (id: string) => post<AdminUser>(`users/${id}/unlock`);

// ---- students ----
export const listStudents = (params: PageQuery & { status?: StudentStatus }) =>
  list<Student>('students', params);
export const getStudent = (id: string) => get<Student>(`students/${id}`);
export const saveStudent = (id: string | undefined, body: StudentInput) =>
  save<Student, StudentInput>('students', id, body);
export const listStudentEnrollments = (id: string) =>
  get<Enrollment[]>(`students/${id}/enrollments`);

// ---- guardians ----
export const listGuardians = (params: PageQuery) => list<Guardian>('guardians', params);
export const saveGuardian = (id: string | undefined, body: GuardianInput) =>
  save<Guardian, GuardianInput>('guardians', id, body);
export const deleteGuardian = async (id: string): Promise<void> => {
  await apiClient.delete(`${BASE}/guardians/${id}`);
};
export const listStudentGuardians = (studentId: string) =>
  get<StudentGuardianLink[]>(`students/${studentId}/guardians`);
export async function saveGuardianLink(
  studentId: string,
  linkId: string | undefined,
  body: GuardianLinkInput,
): Promise<StudentGuardianLink> {
  const path = `students/${studentId}/guardians`;
  const { data } = linkId
    ? await apiClient.put<StudentGuardianLink>(`${BASE}/${path}/${linkId}`, body)
    : await apiClient.post<StudentGuardianLink>(`${BASE}/${path}`, body);
  return data;
}
export const unlinkGuardian = async (studentId: string, linkId: string): Promise<void> => {
  await apiClient.delete(`${BASE}/students/${studentId}/guardians/${linkId}`);
};

// ---- teachers ----
export const listTeachers = (params: PageQuery & { status?: EmploymentStatus }) =>
  list<Teacher>('teachers', params);
export const saveTeacher = (id: string | undefined, body: TeacherInput) =>
  save<Teacher, TeacherInput>('teachers', id, body);

// ---- enrollments and promotion ----
export const listEnrollments = (
  params: PageQuery & {
    academicYearId?: string;
    gradeLevelId?: string;
    sectionId?: string;
    status?: EnrollmentStatus;
  },
) => list<Enrollment>('enrollments', params);
export const enrollStudent = (body: EnrollInput) => post<Enrollment>('enrollments', body);
export const transferEnrollment = (id: string, body: TransferInput) =>
  post<Enrollment>(`enrollments/${id}/transfer`, body);
export const withdrawEnrollment = (id: string) => post<Enrollment>(`enrollments/${id}/withdraw`);
export const completeEnrollment = (id: string) => post<Enrollment>(`enrollments/${id}/complete`);
export const promoteStudents = (body: PromotionInput) => post<PromotionSummary>('promotions', body);
export const listPromotions = (params: PageQuery) => list<PromotionBatch>('promotions', params);
