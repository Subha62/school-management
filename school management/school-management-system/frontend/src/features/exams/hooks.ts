import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { PageResponse } from '../../types/api';
import type { Section } from '../../types/academics';
import type {
  CommentSheet,
  Exam,
  ExamQuery,
  ExamResults,
  GradeRule,
  MarksSheet,
  PagingQuery,
  Progress,
  ReportCard,
  ResultSummary,
  TeacherExam,
} from '../../types/exams';
import * as academics from '../academics/api';
import * as api from './api';

/** Every exam query shares this root, so one change refreshes lists, sheets, progress and results. */
const ROOT = 'exams';

export const useGradeRules = (params: PagingQuery) =>
  useQuery<PageResponse<GradeRule>, ApiError>({
    queryKey: [ROOT, 'grade-rules', params],
    queryFn: () => api.listGradeRules(params),
    placeholderData: keepPreviousData,
  });

export const useExams = (params: ExamQuery) =>
  useQuery<PageResponse<Exam>, ApiError>({
    queryKey: [ROOT, 'list', params],
    queryFn: () => api.listExams(params),
    placeholderData: keepPreviousData,
  });

export const useExam = (id: string) =>
  useQuery<Exam, ApiError>({
    queryKey: [ROOT, 'detail', id],
    queryFn: () => api.getExam(id),
  });

export const useExamProgress = (id: string, enabled = true) =>
  useQuery<Progress, ApiError>({
    queryKey: [ROOT, 'progress', id],
    queryFn: () => api.getProgress(id),
    enabled,
  });

export const useExamResults = (id: string | undefined, sectionId: string | undefined) =>
  useQuery<ExamResults, ApiError>({
    queryKey: [ROOT, 'results', id, sectionId],
    queryFn: () => api.getResults(id as string, sectionId as string),
    enabled: Boolean(id && sectionId),
  });

export const useStudentReportCard = (examId: string, studentId: string) =>
  useQuery<ReportCard, ApiError>({
    queryKey: [ROOT, 'report-card', examId, studentId],
    queryFn: () => api.getStudentReportCard(examId, studentId),
  });

/** The classes of an exam's grade in its academic year. */
export const useExamSections = (
  academicYearId: string | undefined,
  gradeLevelId: string | undefined,
) =>
  useQuery<PageResponse<Section>, ApiError>({
    queryKey: [ROOT, 'sections', academicYearId, gradeLevelId],
    queryFn: () => academics.listSections({ page: 0, size: 100, academicYearId, gradeLevelId }),
    enabled: Boolean(academicYearId && gradeLevelId),
  });

export const useTeacherExams = (params: PagingQuery) =>
  useQuery<PageResponse<TeacherExam>, ApiError>({
    queryKey: [ROOT, 'teacher-list', params],
    queryFn: () => api.listTeacherExams(params),
    placeholderData: keepPreviousData,
  });

export const useMarksSheet = (examId: string, examSubjectId: string, sectionId: string) =>
  useQuery<MarksSheet, ApiError>({
    queryKey: [ROOT, 'marks', examId, examSubjectId, sectionId],
    queryFn: () => api.getMarksSheet(examId, examSubjectId, sectionId),
  });

export const useCommentSheet = (examId: string, sectionId: string) =>
  useQuery<CommentSheet, ApiError>({
    queryKey: [ROOT, 'comments', examId, sectionId],
    queryFn: () => api.getCommentSheet(examId, sectionId),
  });

export const useMyResults = (params: PagingQuery) =>
  useQuery<PageResponse<ResultSummary>, ApiError>({
    queryKey: [ROOT, 'my-results', params],
    queryFn: () => api.listMyResults(params),
    placeholderData: keepPreviousData,
  });

export const useMyReportCard = (examId: string) =>
  useQuery<ReportCard, ApiError>({
    queryKey: [ROOT, 'my-report-card', examId],
    queryFn: () => api.getMyReportCard(examId),
  });

// ---- mutations ----
function useExamMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export const useSaveGradeRule = () =>
  useExamMutation((vars: { id?: string; body: Parameters<typeof api.saveGradeRule>[1] }) =>
    api.saveGradeRule(vars.id, vars.body),
  );
export const useDeleteGradeRule = () => useExamMutation((id: string) => api.deleteGradeRule(id));

export const useSaveExam = () =>
  useExamMutation((vars: { id?: string; body: Parameters<typeof api.saveExam>[1] }) =>
    api.saveExam(vars.id, vars.body),
  );
export const useDeleteExam = () => useExamMutation((id: string) => api.deleteExam(id));
export const useTransitionExam = () =>
  useExamMutation((vars: { id: string; action: Parameters<typeof api.transitionExam>[1] }) =>
    api.transitionExam(vars.id, vars.action),
  );

export const useSaveMarks = () =>
  useExamMutation(
    (vars: {
      examId: string;
      examSubjectId: string;
      sectionId: string;
      body: Parameters<typeof api.saveMarks>[3];
    }) => api.saveMarks(vars.examId, vars.examSubjectId, vars.sectionId, vars.body),
  );
export const useSaveComment = () =>
  useExamMutation((vars: { examId: string; studentId: string; comment: string }) =>
    api.saveComment(vars.examId, vars.studentId, vars.comment),
  );
