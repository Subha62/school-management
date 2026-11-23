import { z } from 'zod';
import { teacherChoiceSchema } from './academics';

const WHOLE_NUMBER = /^\d+$/;

export const DAY_VALUES = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

export const STATUS_VALUES = ['PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED'] as const;

export const timetableSchema = z
  .object({
    subjectId: z.string().min(1, 'Subject is required'),
    teacher: teacherChoiceSchema,
    room: z.string().trim().max(30, 'Room must be at most 30 characters'),
    dayOfWeek: z.enum(DAY_VALUES),
    periodNumber: z
      .string()
      .trim()
      .min(1, 'Period is required')
      .regex(WHOLE_NUMBER, 'Period must be a whole number')
      .refine((value) => Number(value) >= 1, 'Period must be at least 1')
      .refine((value) => Number(value) <= 20, 'Period must be at most 20'),
    startTime: z.string().min(1, 'Start time is required'),
    endTime: z.string().min(1, 'End time is required'),
    effectiveFrom: z.string(),
    effectiveTo: z.string(),
    active: z.boolean(),
  })
  .refine((values) => !values.startTime || !values.endTime || values.endTime > values.startTime, {
    path: ['endTime'],
    message: 'End time must be after the start time',
  })
  .refine(
    (values) =>
      !values.effectiveFrom || !values.effectiveTo || values.effectiveTo >= values.effectiveFrom,
    { path: ['effectiveTo'], message: 'The end date cannot be before the start date' },
  );
export type TimetableFormValues = z.infer<typeof timetableSchema>;

export const correctionSchema = z.object({
  status: z.enum(STATUS_VALUES),
  remarks: z.string().trim().max(200, 'Remarks must be at most 200 characters'),
  reason: z
    .string()
    .trim()
    .min(5, 'Explain the reason in at least 5 characters')
    .max(500, 'The reason must be at most 500 characters'),
});
export type CorrectionFormValues = z.infer<typeof correctionSchema>;
