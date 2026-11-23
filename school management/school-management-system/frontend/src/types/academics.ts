export type AcademicYearStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED';

export interface NamedRef {
  id: string;
  name: string;
}

export interface AcademicYear {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: AcademicYearStatus;
  version: number;
}

export interface GradeLevel {
  id: string;
  name: string;
  displayOrder: number;
  active: boolean;
  version: number;
}

export interface Subject {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  version: number;
}

export interface Section {
  id: string;
  academicYear: NamedRef;
  gradeLevel: NamedRef;
  name: string;
  capacity: number;
  classTeacher: NamedRef | null;
  active: boolean;
  version: number;
}

export interface SectionSubject {
  id: string;
  academicYearId: string;
  academicYearName: string;
  sectionId: string;
  sectionName: string;
  gradeLevelName: string;
  subject: { id: string; code: string; name: string };
  teacher: NamedRef | null;
  weeklyPeriods: number;
  active: boolean;
  version: number;
}

export interface TeacherOption {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

/** `version` is required when updating an existing record and omitted when creating one. */
export interface AcademicYearInput {
  name: string;
  startDate: string;
  endDate: string;
  version?: number;
}

export interface GradeInput {
  name: string;
  displayOrder: number;
  active: boolean;
  version?: number;
}

export interface SubjectInput {
  code: string;
  name: string;
  description: string;
  active: boolean;
  version?: number;
}

export interface SectionInput {
  academicYearId?: string;
  gradeLevelId?: string;
  name: string;
  capacity: number;
  classTeacherId: string | null;
  active: boolean;
  version?: number;
}

export interface AssignmentInput {
  sectionId?: string;
  subjectId?: string;
  teacherId: string | null;
  weeklyPeriods: number;
  active: boolean;
  version?: number;
}

export interface PageQuery {
  page: number;
  size: number;
  search?: string;
}
