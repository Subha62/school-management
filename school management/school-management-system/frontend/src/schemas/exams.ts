import { z } from 'zod';
import { maxMarksSchema } from './assignments';

const MARKS_PATTERN = /^\d{1,4}(\.\d{1,2})?$/;
const PERCENT_PATTERN = /^\d{1,3}(\.\d{1,2})?$/;
const DECIMALS_MESSAGE = 'Use a number with at most two decimal places';

/** Share of the maximum marks that is suggested as the pass mark until the user types one. */
const DEFAULT_PASS_SHARE_PERCENT = 35;
export const MAX_REMARKS = 200;
export const MAX_COMMENT = 500;
export const MAX_EXAM_SUBJECTS = 30;

const percentField = (label: string) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .regex(PERCENT_PATTERN, DECIMALS_MESSAGE)
    .refine((value) => Number(value) <= 100, `${label} must be at most 100`);

export const gradeRuleSchema = z
  .object({
    grade: z
      .string()
      .trim()
      .min(1, 'Grade is required')
      .max(10, 'Grade must be at most 10 characters'),
    description: z.string().trim().max(100, 'Description must be at most 100 characters'),
    minPercent: percentField('Minimum'),
    maxPercent: percentField('Maximum'),
  })
  .refine(
    (values) =>
      !values.minPercent ||
      !values.maxPercent ||
      Number(values.minPercent) <= Number(values.maxPercent),
    {
      path: ['maxPercent'],
      message: 'Maximum must not be below the minimum',
    },
  );
export type GradeRuleFormValues = z.infer<typeof gradeRuleSchema>;

/** 35% of the maximum marks as text, or an empty string while the maximum is not a usable number. */
export function defaultPassMarks(maxMarks: string): string {
  const max = Number(maxMarks.trim());
  if (!Number.isFinite(max) || max <= 0) return '';
  return String(Math.round(max * DEFAULT_PASS_SHARE_PERCENT) / 100);
}

const examSubjectSchema = z
  .object({
    subjectId: z.string().min(1, 'Subject is required'),
    maxMarks: maxMarksSchema,
    passMarks: z
      .string()
      .trim()
      .min(1, 'Pass marks are required')
      .regex(MARKS_PATTERN, DECIMALS_MESSAGE),
    examDate: z.string(),
    /** True once the user has typed pass marks, so they are no longer derived from the maximum marks. */
    passEdited: z.boolean(),
  })
  .refine(
    (row) =>
      !MARKS_PATTERN.test(row.passMarks) ||
      !MARKS_PATTERN.test(row.maxMarks) ||
      Number(row.passMarks) <= Number(row.maxMarks),
    { path: ['passMarks'], message: 'Pass marks cannot be more than the maximum marks' },
  );
export type ExamSubjectFormValues = z.infer<typeof examSubjectSchema>;

/** The academic year and grade are chosen when creating an exam; they cannot change afterwards. */
export const examSchema = (creating: boolean) =>
  z
    .object({
      academicYearId: creating ? z.string().min(1, 'Academic year is required') : z.string(),
      gradeLevelId: creating ? z.string().min(1, 'Grade is required') : z.string(),
      name: z
        .string()
        .trim()
        .min(1, 'Name is required')
        .max(100, 'Name must be at most 100 characters'),
      startDate: z.string(),
      endDate: z.string(),
      subjects: z
        .array(examSubjectSchema)
        .min(1, 'Add at least one subject')
        .max(MAX_EXAM_SUBJECTS, `An exam can have at most ${MAX_EXAM_SUBJECTS} subjects`),
    })
    .superRefine((values, context) => {
      if (values.startDate && values.endDate && values.endDate < values.startDate) {
        context.addIssue({
          code: 'custom',
          path: ['endDate'],
          message: 'End date cannot be before the start date',
        });
      }
      const seen = new Set<string>();
      values.subjects.forEach((row, index) => {
        if (!row.subjectId) return;
        if (seen.has(row.subjectId)) {
          context.addIssue({
            code: 'custom',
            path: ['subjects', index, 'subjectId'],
            message: 'Each subject can only be added once',
          });
        }
        seen.add(row.subjectId);
      });
    });
export type ExamFormValues = z.infer<ReturnType<typeof examSchema>>;

// ---- marks entry ----
/** One student's entry as typed: marks are text until they are validated and sent. */
export interface MarkDraft {
  marks: string;
  absent: boolean;
  remarks: string;
}

export interface MarkErrors {
  marks?: string;
  remarks?: string;
}

export const markDraftSchema = (maxMarks: number) =>
  z
    .object({
      marks: z.string().trim(),
      absent: z.boolean(),
      remarks: z
        .string()
        .trim()
        .max(MAX_REMARKS, `Remarks must be at most ${MAX_REMARKS} characters`),
    })
    .superRefine((draft, context) => {
      if (draft.marks === '') return;
      const issue = (message: string) =>
        context.addIssue({ code: 'custom', path: ['marks'], message });
      if (draft.absent) issue('An absent student has no marks');
      else if (draft.marks.startsWith('-')) issue('Marks cannot be negative');
      else if (!MARKS_PATTERN.test(draft.marks)) issue(DECIMALS_MESSAGE);
      else if (Number(draft.marks) > maxMarks) issue(`Marks cannot be more than ${maxMarks}`);
    });

/** The first problem of each field, or an empty object when the entry can be saved. */
export function validateMarkDraft(draft: MarkDraft, maxMarks: number): MarkErrors {
  const result = markDraftSchema(maxMarks).safeParse(draft);
  if (result.success) return {};
  const errors: MarkErrors = {};
  for (const issue of result.error.issues) {
    const field = issue.path[0];
    if ((field === 'marks' || field === 'remarks') && !errors[field]) errors[field] = issue.message;
  }
  return errors;
}

/** Marks are blank for an absent student and for a cleared entry. */
export function toMarkEntry(studentId: string, draft: MarkDraft) {
  const marks = draft.absent || draft.marks.trim() === '' ? null : Number(draft.marks);
  return {
    studentId,
    marks,
    absent: draft.absent,
    remarks: draft.remarks.trim() === '' ? null : draft.remarks.trim(),
  };
}

export const commentSchema = z.object({
  comment: z
    .string()
    .trim()
    .max(MAX_COMMENT, `The comment must be at most ${MAX_COMMENT} characters`),
});
