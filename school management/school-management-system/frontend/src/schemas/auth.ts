import { z } from 'zod';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD_LENGTH = 10;
const MAX_PASSWORD_BYTES = 72;

const utf8Length = (value: string): number => new TextEncoder().encode(value).length;

/** Mirrors the backend @StrongPassword rule; the server remains the authority. */
export const newPasswordSchema = z
  .string()
  .min(1, 'New password is required')
  .min(MIN_PASSWORD_LENGTH, `Password must be at least ${MIN_PASSWORD_LENGTH} characters`)
  .refine((value) => utf8Length(value) <= MAX_PASSWORD_BYTES, 'Password is too long')
  .refine((value) => /[a-z]/.test(value), 'Include a lower case letter')
  .refine((value) => /[A-Z]/.test(value), 'Include an upper case letter')
  .refine((value) => /\d/.test(value), 'Include a digit')
  .refine((value) => /[^\p{L}\p{N}\s]/u.test(value), 'Include a special character');

export const loginSchema = z.object({
  identifier: z
    .string()
    .trim()
    .min(1, 'Email or ID is required')
    .max(254, 'Email or ID is too long'),
  password: z.string().min(1, 'Password is required').max(128, 'Password is too long'),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

export const forgotPasswordSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, { error: 'Email is required', abort: true })
    .refine((value) => EMAIL_PATTERN.test(value), 'Enter a valid email address'),
});
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: newPasswordSchema,
    confirmPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  });
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required').max(128),
    newPassword: newPasswordSchema,
    confirmPassword: z.string().min(1, 'Confirm your new password'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Passwords do not match',
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'The new password must be different from the current one',
  });
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;
