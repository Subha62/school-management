import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type {
  CommentRow,
  CommentSheet,
  Exam,
  ExamAction,
  ExamInput,
  ExamQuery,
  ExamResults,
  GradeRule,
  GradeRuleInput,
  MarksSheet,
  PagingQuery,
  Progress,
  ReportCard,
  ResultSummary,
  SaveMarksRequest,
  TeacherExam,
} from '../../types/exams';

const ADMIN = '/admin';
const TEACHER = '/teacher';
const STUDENT = '/student';

async function get<T>(path: string, params?: object): Promise<T> {
  const { data } = await apiClient.get<T>(path, { params });
  return data;
}

async function post<T>(path: string): Promise<T> {
  const { data } = await apiClient.post<T>(path);
  return data;
}

// ---- grade rules ----
export const listGradeRules = (params: PagingQuery) =>
  get<PageResponse<GradeRule>>(`${ADMIN}/grade-rules`, params);

export async function saveGradeRule(id: string | undefined, body: GradeRuleInput) {
  const { data } = id
    ? await apiClient.put<GradeRule>(`${ADMIN}/grade-rules/${id}`, body)
    : await apiClient.post<GradeRule>(`${ADMIN}/grade-rules`, body);
  return data;
}

export const deleteGradeRule = async (id: string): Promise<void> => {
  await apiClient.delete(`${ADMIN}/grade-rules/${id}`);
};

// ---- exams (administrators) ----
export const listExams = (params: ExamQuery) => get<PageResponse<Exam>>(`${ADMIN}/exams`, params);
export const getExam = (id: string) => get<Exam>(`${ADMIN}/exams/${id}`);

export async function saveExam(id: string | undefined, body: ExamInput): Promise<Exam> {
  const { data } = id
    ? await apiClient.put<Exam>(`${ADMIN}/exams/${id}`, body)
    : await apiClient.post<Exam>(`${ADMIN}/exams`, body);
  return data;
}

export const deleteExam = async (id: string): Promise<void> => {
  await apiClient.delete(`${ADMIN}/exams/${id}`);
};

export const transitionExam = (id: string, action: ExamAction) =>
  post<Exam>(`${ADMIN}/exams/${id}/${action}`);

export const getProgress = (id: string) => get<Progress>(`${ADMIN}/exams/${id}/progress`);
export const getResults = (id: string, sectionId: string) =>
  get<ExamResults>(`${ADMIN}/exams/${id}/results`, { sectionId });
export const getStudentReportCard = (examId: string, studentId: string) =>
  get<ReportCard>(`${ADMIN}/exams/${examId}/results/${studentId}`);

// ---- teacher ----
export const listTeacherExams = (params: PagingQuery) =>
  get<PageResponse<TeacherExam>>(`${TEACHER}/exams`, params);

export const getMarksSheet = (examId: string, examSubjectId: string, sectionId: string) =>
  get<MarksSheet>(`${TEACHER}/exams/${examId}/marks`, { examSubjectId, sectionId });

export async function saveMarks(
  examId: string,
  examSubjectId: string,
  sectionId: string,
  body: SaveMarksRequest,
): Promise<MarksSheet> {
  const { data } = await apiClient.put<MarksSheet>(`${TEACHER}/exams/${examId}/marks`, body, {
    params: { examSubjectId, sectionId },
  });
  return data;
}

export const getCommentSheet = (examId: string, sectionId: string) =>
  get<CommentSheet>(`${TEACHER}/exams/${examId}/comments`, { sectionId });

export async function saveComment(
  examId: string,
  studentId: string,
  comment: string,
): Promise<CommentRow> {
  const { data } = await apiClient.put<CommentRow>(
    `${TEACHER}/exams/${examId}/comments/${studentId}`,
    { comment },
  );
  return data;
}

// ---- student ----
export const listMyResults = (params: PagingQuery) =>
  get<PageResponse<ResultSummary>>(`${STUDENT}/results`, params);
export const getMyReportCard = (examId: string) => get<ReportCard>(`${STUDENT}/results/${examId}`);
