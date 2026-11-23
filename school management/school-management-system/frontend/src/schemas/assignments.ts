import { z } from 'zod';

const MARKS_PATTERN = /^\d{1,4}(\.\d{1,2})?$/;

export const maxMarksSchema = z
  .string()
  .trim()
  .min(1, 'Maximum marks are required')
  .regex(MARKS_PATTERN, 'Use a number with at most two decimal places')
  .refine((value) => Number(value) >= 0.01, 'Maximum marks must be more than 0')
  .refine((value) => Number(value) <= 1000, 'Maximum marks must be at most 1000');

/**
 * `originalDue` is the due date the assignment already has (a `datetime-local` value). Keeping it unchanged is
 * always allowed, so a published assignment whose date has passed can still be edited.
 */
export const assignmentSchema = (
  creating: boolean,
  originalDue: string | null,
  now: () => number,
) =>
  z
    .object({
      target: creating ? z.string().min(1, 'Choose a class and subject') : z.string(),
      title: z
        .string()
        .trim()
        .min(1, 'Title is required')
        .max(200, 'Title must be at most 200 characters'),
      description: z.string().trim().max(5000, 'Instructions must be at most 5000 characters'),
      dueAt: z.string().min(1, 'Due date is required'),
      maxMarks: maxMarksSchema,
      allowLate: z.boolean(),
    })
    .refine(
      (values) =>
        !values.dueAt || values.dueAt === originalDue || new Date(values.dueAt).getTime() > now(),
      { path: ['dueAt'], message: 'The due date must be in the future' },
    );
export type AssignmentFormValues = z.infer<ReturnType<typeof assignmentSchema>>;

export type ReviewChoice = 'REVIEWED' | 'RESUBMISSION_REQUESTED';

/** Marks are needed to complete a review; feedback is needed to ask for a resubmission. */
export const reviewSchema = (choice: ReviewChoice, maxMarks: number) =>
  z
    .object({
      marks: z.string().trim(),
      feedback: z.string().trim().max(2000, 'Feedback must be at most 2000 characters'),
    })
    .superRefine((values, context) => {
      if (choice === 'REVIEWED') {
        if (values.marks === '') {
          context.addIssue({ code: 'custom', path: ['marks'], message: 'Marks are required' });
        } else if (!MARKS_PATTERN.test(values.marks)) {
          context.addIssue({
            code: 'custom',
            path: ['marks'],
            message: 'Use a number with at most two decimal places',
          });
        } else if (Number(values.marks) > maxMarks) {
          context.addIssue({
            code: 'custom',
            path: ['marks'],
            message: `Marks cannot be more than ${maxMarks}`,
          });
        }
      } else if (values.feedback === '') {
        context.addIssue({
          code: 'custom',
          path: ['feedback'],
          message: 'Say what the student should improve',
        });
      }
    });
export type ReviewFormValues = z.infer<ReturnType<typeof reviewSchema>>;

export const answerSchema = z.object({
  textAnswer: z.string().max(10000, 'The answer must be at most 10000 characters'),
});
export type AnswerFormValues = z.infer<typeof answerSchema>;
