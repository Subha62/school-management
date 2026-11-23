export type ExamStatus = 'DRAFT' | 'OPEN_FOR_MARKS' | 'LOCKED' | 'PUBLISHED';
export type SubjectStatus = 'PASS' | 'FAIL' | 'ABSENT';
export type ResultOutcome = 'PASS' | 'FAIL';
export type ExamAction = 'open' | 'lock' | 'reopen' | 'publish';

// ---- grade scale ----
export interface GradeRule {
  id: string;
  grade: string;
  description: string | null;
  minPercent: number;
  maxPercent: number;
  version: number;
}

export interface GradeRuleInput {
  grade: string;
  description: string | null;
  minPercent: number;
  maxPercent: number;
  version?: number;
}

// ---- exams (administrators) ----
export interface ExamSubject {
  id: string;
  subjectId: string;
  subjectCode: string;
  subjectName: string;
  maxMarks: number;
  passMarks: number;
  examDate: string | null;
}

export interface Exam {
  id: string;
  academicYearId: string;
  academicYearName: string;
  gradeLevelId: string;
  gradeLevelName: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: ExamStatus;
  subjects: ExamSubject[];
  lockedAt: string | null;
  publishedAt: string | null;
  version: number;
}

export interface ExamSubjectInput {
  subjectId: string;
  maxMarks: number;
  passMarks: number;
  examDate: string | null;
}

/** The year and grade are only sent when creating; the server ignores them on update. */
export interface ExamInput {
  academicYearId?: string;
  gradeLevelId?: string;
  name: string;
  startDate: string | null;
  endDate: string | null;
  subjects: ExamSubjectInput[];
  version?: number;
}

export interface ExamQuery {
  page: number;
  size: number;
  sort?: 'name' | 'status' | 'startDate' | 'createdAt';
  direction?: 'asc' | 'desc';
  academicYearId?: string;
  gradeLevelId?: string;
  status?: ExamStatus;
  search?: string;
}

export interface PagingQuery {
  page: number;
  size: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export interface ProgressRow {
  examSubjectId: string;
  subjectName: string;
  sectionId: string;
  sectionName: string;
  teacherName: string | null;
  expected: number;
  entered: number;
}

export interface Progress {
  examId: string;
  status: ExamStatus;
  complete: boolean;
  missing: number;
  rows: ProgressRow[];
}

// ---- marks entry (teachers) ----
export interface SectionRef {
  id: string;
  name: string;
}

export interface ExamTask {
  examSubjectId: string;
  subjectName: string;
  sectionId: string;
  sectionName: string;
  maxMarks: number;
  passMarks: number;
  expected: number;
  entered: number;
}

export interface TeacherExam {
  id: string;
  name: string;
  status: ExamStatus;
  academicYearName: string;
  gradeLevelName: string;
  startDate: string | null;
  endDate: string | null;
  tasks: ExamTask[];
  commentSections: SectionRef[];
}

export interface MarkRow {
  studentId: string;
  admissionNumber: string;
  studentName: string;
  rollNumber: number | null;
  marks: number | null;
  absent: boolean;
  remarks: string | null;
  entered: boolean;
}

export interface MarksSheet {
  examId: string;
  examName: string;
  status: ExamStatus;
  examSubjectId: string;
  subjectName: string;
  sectionId: string;
  sectionName: string;
  maxMarks: number;
  passMarks: number;
  editable: boolean;
  rows: MarkRow[];
}

export interface MarkEntry {
  studentId: string;
  marks: number | null;
  absent: boolean;
  remarks: string | null;
}

export interface SaveMarksRequest {
  entries: MarkEntry[];
}

export interface CommentRow {
  studentId: string;
  admissionNumber: string;
  studentName: string;
  rollNumber: number | null;
  comment: string | null;
}

export interface CommentSheet {
  examId: string;
  examName: string;
  status: ExamStatus;
  sectionId: string;
  sectionName: string;
  editable: boolean;
  rows: CommentRow[];
}

// ---- results ----
export interface SubjectResult {
  subjectId: string;
  subjectCode: string;
  subjectName: string;
  maxMarks: number;
  passMarks: number;
  marks: number | null;
  absent: boolean;
  percentage: number | null;
  grade: string | null;
  status: SubjectStatus;
  remarks: string | null;
}

export interface StudentResult {
  studentId: string;
  admissionNumber: string;
  studentName: string;
  sectionId: string;
  sectionName: string;
  rollNumber: number | null;
  subjects: SubjectResult[];
  totalMarks: number;
  totalMax: number;
  percentage: number;
  grade: string | null;
  result: ResultOutcome;
  comment: string | null;
}

/** `preview` is true while the exam is locked and nothing is frozen yet. */
export interface ExamResults {
  examId: string;
  examName: string;
  status: ExamStatus;
  preview: boolean;
  sectionId: string;
  results: StudentResult[];
}

export interface ReportCard {
  examId: string;
  examName: string;
  schoolName: string;
  academicYearName: string;
  gradeLevelName: string;
  startDate: string | null;
  endDate: string | null;
  publishedAt: string | null;
  result: StudentResult;
}

export interface ResultSummary {
  examId: string;
  examName: string;
  academicYearName: string;
  gradeLevelName: string;
  publishedAt: string | null;
  percentage: number;
  grade: string | null;
  result: ResultOutcome;
}
