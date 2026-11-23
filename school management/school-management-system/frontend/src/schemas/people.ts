import { z } from 'zod';
import { newPasswordSchema } from './auth';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// The same formats the API accepts.
const PHONE_PATTERN = /^[0-9+()\-\s]{6,30}$/;
const BLOOD_GROUP_PATTERN = /^(A|B|AB|O)[+-]$/;
const WHOLE_NUMBER = /^\d+$/;

const requiredText = (label: string, max: number) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .max(max, `${label} must be at most ${max} characters`);

const optionalText = (label: string, max: number) =>
  z.string().trim().max(max, `${label} must be at most ${max} characters`);

const email = z
  .string()
  .trim()
  .min(1, { error: 'Email is required', abort: true })
  .max(254, 'Email is too long')
  .refine((value) => EMAIL_PATTERN.test(value), 'Enter a valid email address');

const requiredPhone = (label: string) =>
  z
    .string()
    .trim()
    .min(1, { error: `${label} is required`, abort: true })
    .refine((value) => PHONE_PATTERN.test(value), 'Phone number is not valid');

const optionalPhone = z
  .string()
  .trim()
  .refine((value) => value === '' || PHONE_PATTERN.test(value), 'Phone number is not valid');

const requiredDate = (label: string) => z.string().min(1, `${label} is required`);

const optionalRollNumber = z
  .string()
  .trim()
  .refine((value) => value === '' || WHOLE_NUMBER.test(value), 'Roll number must be a whole number')
  .refine(
    (value) => value === '' || (Number(value) >= 1 && Number(value) <= 9999),
    'Roll number must be between 1 and 9999',
  );

export const GENDERS = ['MALE', 'FEMALE', 'OTHER'] as const;
export const STUDENT_STATUSES = ['ACTIVE', 'INACTIVE', 'WITHDRAWN', 'GRADUATED'] as const;
export const EMPLOYMENT_STATUSES = ['ACTIVE', 'ON_LEAVE', 'RESIGNED', 'RETIRED'] as const;
export const RELATIONSHIPS = [
  'FATHER',
  'MOTHER',
  'GUARDIAN',
  'GRANDPARENT',
  'SIBLING',
  'OTHER',
] as const;

const studentFields = {
  firstName: requiredText('First name', 100),
  lastName: requiredText('Last name', 100),
  email,
  phoneNumber: optionalPhone,
  dateOfBirth: requiredDate('Date of birth'),
  gender: z.enum(GENDERS),
  admissionDate: requiredDate('Admission date'),
  bloodGroup: z
    .string()
    .trim()
    .refine(
      (value) => value === '' || BLOOD_GROUP_PATTERN.test(value),
      'Use a group such as O+ or AB-',
    ),
  addressLine1: requiredText('Address', 200),
  addressLine2: optionalText('Address', 200),
  city: requiredText('City', 100),
  state: optionalText('State', 100),
  postalCode: optionalText('Postal code', 20),
  emergencyContactName: requiredText('Emergency contact name', 100),
  emergencyContactPhone: requiredPhone('Emergency contact phone'),
};

const admissionAfterBirth = {
  path: ['admissionDate'],
  message: 'Admission date cannot be before the date of birth',
};

/** One schema for both modes: the admission number and password only matter (and are only sent) when creating. */
export const studentSchema = (creating: boolean) =>
  z
    .object({
      admissionNumber: creating ? requiredText('Admission number', 30) : z.string(),
      password: creating ? newPasswordSchema : z.string(),
      status: z.enum(STUDENT_STATUSES),
      ...studentFields,
    })
    .refine(
      (v) => !v.dateOfBirth || !v.admissionDate || v.admissionDate >= v.dateOfBirth,
      admissionAfterBirth,
    );
export type StudentFormValues = z.infer<ReturnType<typeof studentSchema>>;

const teacherFields = {
  firstName: requiredText('First name', 100),
  lastName: requiredText('Last name', 100),
  email,
  phoneNumber: optionalPhone,
  joiningDate: requiredDate('Joining date'),
  qualification: optionalText('Qualification', 200),
  specialization: optionalText('Specialization', 200),
  employmentStatus: z.enum(EMPLOYMENT_STATUSES),
};

export const teacherSchema = (creating: boolean) =>
  z.object({
    employeeId: creating ? requiredText('Employee ID', 30) : z.string(),
    password: creating ? newPasswordSchema : z.string(),
    ...teacherFields,
  });
export type TeacherFormValues = z.infer<ReturnType<typeof teacherSchema>>;
export const guardianSchema = z.object({
  firstName: requiredText('First name', 100),
  lastName: requiredText('Last name', 100),
  relationship: z.enum(RELATIONSHIPS),
  email: z
    .string()
    .trim()
    .max(254, 'Email is too long')
    .refine((value) => value === '' || EMAIL_PATTERN.test(value), 'Enter a valid email address'),
  phoneNumber: requiredPhone('Phone number'),
  occupation: optionalText('Occupation', 100),
  address: optionalText('Address', 300),
  emergencyContact: z.boolean(),
});
export type GuardianFormValues = z.infer<typeof guardianSchema>;

const choice = z.object({ id: z.string(), label: z.string() });
export type ChoiceValue = z.infer<typeof choice>;

export const guardianLinkSchema = (creating: boolean) =>
  z.object({
    guardian: creating
      ? choice.nullable().refine((value): boolean => value !== null, 'Select a guardian')
      : choice.nullable(),
    primaryGuardian: z.boolean(),
    receivesNotifications: z.boolean(),
    authorizedPickup: z.boolean(),
  });
export type GuardianLinkFormValues = z.infer<ReturnType<typeof guardianLinkSchema>>;

export const adminSchema = z.object({
  firstName: requiredText('First name', 100),
  lastName: requiredText('Last name', 100),
  email,
  phoneNumber: optionalPhone,
  password: newPasswordSchema,
});
export type AdminFormValues = z.infer<typeof adminSchema>;

export type StudentChoice = ChoiceValue;

export const enrollSchema = z.object({
  student: choice.nullable().refine((value): boolean => value !== null, 'Select a student'),
  academicYearId: z.string().min(1, 'Academic year is required'),
  sectionId: z.string().min(1, 'Section is required'),
  rollNumber: optionalRollNumber,
});
export type EnrollFormValues = z.infer<typeof enrollSchema>;

export const transferSchema = z.object({
  targetSectionId: z.string().min(1, 'Section is required'),
  rollNumber: optionalRollNumber,
});
export type TransferFormValues = z.infer<typeof transferSchema>;

/** Empty optional text is sent as null, because the API validates the format of any text it receives. */
export const blankToNull = (value: string): string | null => {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
};

export const rollNumberOrNull = (value: string): number | null =>
  value.trim() === '' ? null : Number(value);
