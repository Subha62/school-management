import type { PageResponse } from '../../types/api';
import type {
  AcademicYear,
  AcademicYearInput,
  AcademicYearStatus,
  AssignmentInput,
  GradeInput,
  GradeLevel,
  PageQuery,
  Section,
  SectionInput,
  SectionSubject,
  Subject,
  SubjectInput,
  TeacherOption,
} from '../../types/academics';
import { apiClient } from '../../api/client';

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

async function remove(path: string, id: string): Promise<void> {
  await apiClient.delete(`${BASE}/${path}/${id}`);
}

// ---- academic years ----
export const listAcademicYears = (params: PageQuery & { status?: AcademicYearStatus }) =>
  list<AcademicYear>('academic-years', params);
export const saveAcademicYear = (id: string | undefined, body: AcademicYearInput) =>
  save<AcademicYear, AcademicYearInput>('academic-years', id, body);
export const deleteAcademicYear = (id: string) => remove('academic-years', id);
export async function activateAcademicYear(id: string): Promise<AcademicYear> {
  const { data } = await apiClient.post<AcademicYear>(`${BASE}/academic-years/${id}/activate`);
  return data;
}
export async function closeAcademicYear(id: string): Promise<AcademicYear> {
  const { data } = await apiClient.post<AcademicYear>(`${BASE}/academic-years/${id}/close`);
  return data;
}

// ---- grades ----
export const listGrades = (params: PageQuery & { active?: boolean }) =>
  list<GradeLevel>('grade-levels', params);
export const saveGrade = (id: string | undefined, body: GradeInput) =>
  save<GradeLevel, GradeInput>('grade-levels', id, body);
export const deleteGrade = (id: string) => remove('grade-levels', id);

// ---- subjects ----
export const listSubjects = (params: PageQuery & { active?: boolean }) =>
  list<Subject>('subjects', params);
export const saveSubject = (id: string | undefined, body: SubjectInput) =>
  save<Subject, SubjectInput>('subjects', id, body);
export const deleteSubject = (id: string) => remove('subjects', id);

// ---- sections ----
export const listSections = (
  params: PageQuery & { academicYearId?: string; gradeLevelId?: string; active?: boolean },
) => list<Section>('sections', params);
export const saveSection = (id: string | undefined, body: SectionInput) =>
  save<Section, SectionInput>('sections', id, body);
export const deleteSection = (id: string) => remove('sections', id);

// ---- subject assignments ----
export const listAssignments = (
  params: PageQuery & { academicYearId?: string; sectionId?: string; teacherId?: string },
) => list<SectionSubject>('section-subjects', params);
export const saveAssignment = (id: string | undefined, body: AssignmentInput) =>
  save<SectionSubject, AssignmentInput>('section-subjects', id, body);
export const deleteAssignment = (id: string) => remove('section-subjects', id);

// ---- teachers ----
export const listTeacherOptions = (params: PageQuery) =>
  list<TeacherOption>('teacher-options', params);
