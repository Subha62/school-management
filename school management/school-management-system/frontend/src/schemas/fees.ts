import { z } from 'zod';

const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;
const METHODS = ['CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'CARD', 'OTHER'] as const;

export const MIN_REASON = 5;
export const MAX_REASON = 300;

/** A positive amount typed as text, with at most two decimals. The server still decides what is allowed. */
export const moneySchema = z
  .string()
  .trim()
  .min(1, 'Amount is required')
  .regex(MONEY_PATTERN, 'Use a number with at most two decimal places')
  .refine((value) => Number(value) > 0, 'Amount must be more than 0');

export const categorySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Name is required')
    .max(100, 'Name must be at most 100 characters'),
  description: z.string().trim().max(300, 'Description must be at most 300 characters'),
  active: z.boolean(),
});
export type CategoryFormValues = z.infer<typeof categorySchema>;

/** The year, grade and category are chosen when creating a structure and cannot change afterwards. */
export const structureSchema = (creating: boolean) =>
  z.object({
    academicYearId: creating ? z.string().min(1, 'Academic year is required') : z.string(),
    gradeLevelId: creating ? z.string().min(1, 'Grade is required') : z.string(),
    feeCategoryId: creating ? z.string().min(1, 'Fee category is required') : z.string(),
    description: z.string().trim().max(200, 'Description must be at most 200 characters'),
    amount: moneySchema,
    dueDate: z.string().min(1, 'Due date is required'),
    active: z.boolean(),
  });
export type StructureFormValues = z.infer<ReturnType<typeof structureSchema>>;

/** `today` is the local calendar date (YYYY-MM-DD); a payment cannot be dated in the future. */
export const paymentSchema = (today: string) =>
  z.object({
    amount: moneySchema,
    method: z.enum(METHODS, { error: 'Payment method is required' }),
    reference: z.string().trim().max(100, 'Reference must be at most 100 characters'),
    paidOn: z
      .string()
      .min(1, 'Payment date is required')
      .refine((value) => value <= today, 'The payment date cannot be in the future'),
  });
export type PaymentFormValues = z.infer<ReturnType<typeof paymentSchema>>;

export const reasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, 'A reason is required')
    .min(MIN_REASON, `The reason must be at least ${MIN_REASON} characters`)
    .max(MAX_REASON, `The reason must be at most ${MAX_REASON} characters`),
});
export type ReasonFormValues = z.infer<typeof reasonSchema>;
