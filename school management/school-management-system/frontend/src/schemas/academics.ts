import { z } from 'zod';

const WHOLE_NUMBER = /^\d+$/;
const SUBJECT_CODE = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

const requiredText = (label: string, max: number) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .max(max, `${label} must be at most ${max} characters`);

const wholeNumberBetween = (label: string, min: number, max: number) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .regex(WHOLE_NUMBER, `${label} must be a whole number`)
    .refine((value) => Number(value) >= min, `${label} must be at least ${min}`)
    .refine((value) => Number(value) <= max, `${label} must be at most ${max}`);

export const academicYearSchema = z
  .object({
    name: requiredText('Name', 50),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string().min(1, 'End date is required'),
  })
  .refine((values) => !values.startDate || !values.endDate || values.endDate > values.startDate, {
    path: ['endDate'],
    message: 'End date must be after the start date',
  });
export type AcademicYearFormValues = z.infer<typeof academicYearSchema>;

export const gradeSchema = z.object({
  name: requiredText('Name', 50),
  displayOrder: wholeNumberBetween('Display order', 0, 1000),
  active: z.boolean(),
});
export type GradeFormValues = z.infer<typeof gradeSchema>;

export const subjectSchema = z.object({
  code: requiredText('Code', 20).regex(
    SUBJECT_CODE,
    'Use letters, digits, dots, dashes or underscores',
  ),
  name: requiredText('Name', 100),
  description: z.string().trim().max(500, 'Description must be at most 500 characters'),
  active: z.boolean(),
});
export type SubjectFormValues = z.infer<typeof subjectSchema>;

/** A teacher picked in an autocomplete: the id is sent to the API, the label is only displayed. */
export const teacherChoiceSchema = z.object({ id: z.string(), label: z.string() }).nullable();
export type TeacherChoice = z.infer<typeof teacherChoiceSchema>;

export const sectionSchema = z.object({
  academicYearId: z.string().min(1, 'Academic year is required'),
  gradeLevelId: z.string().min(1, 'Grade is required'),
  name: requiredText('Name', 20),
  capacity: wholeNumberBetween('Capacity', 1, 500),
  classTeacher: teacherChoiceSchema,
  active: z.boolean(),
});
export type SectionFormValues = z.infer<typeof sectionSchema>;

export const assignmentSchema = z.object({
  subjectId: z.string().min(1, 'Subject is required'),
  teacher: teacherChoiceSchema,
  weeklyPeriods: wholeNumberBetween('Weekly periods', 1, 40),
  active: z.boolean(),
});
export type AssignmentFormValues = z.infer<typeof assignmentSchema>;
