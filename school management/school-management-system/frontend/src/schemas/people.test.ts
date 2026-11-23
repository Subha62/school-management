import { adminSchema, enrollSchema, studentSchema, transferSchema } from './people';
import { generatePassword } from '../utils/password';
import { newPasswordSchema } from './auth';

const validStudent = {
  admissionNumber: 'S-200',
  password: 'Correct-Pass-1!',
  status: 'ACTIVE' as const,
  firstName: 'Ola',
  lastName: 'Okoye',
  email: 'ola@school.test',
  phoneNumber: '',
  dateOfBirth: '2012-04-10',
  gender: 'MALE' as const,
  admissionDate: '2026-06-01',
  bloodGroup: '',
  addressLine1: '1 Main Road',
  addressLine2: '',
  city: 'Pune',
  state: '',
  postalCode: '',
  emergencyContactName: 'Parent',
  emergencyContactPhone: '+91 98765 00000',
};

describe('studentSchema', () => {
  it('accepts a complete student with blank optional fields', () => {
    expect(studentSchema(true).safeParse(validStudent).success).toBe(true);
  });

  it('requires an admission number and a strong password only when creating', () => {
    const blank = { ...validStudent, admissionNumber: '', password: '' };
    expect(studentSchema(true).safeParse(blank).success).toBe(false);
    expect(studentSchema(false).safeParse(blank).success).toBe(true);
  });

  it('rejects an admission date before the date of birth', () => {
    const result = studentSchema(false).safeParse({ ...validStudent, admissionDate: '2010-01-01' });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['admissionDate']);
  });

  it.each([
    ['phoneNumber', 'abc'],
    ['bloodGroup', 'C+'],
    ['emergencyContactPhone', '12'],
    ['email', 'not-an-email'],
  ])('rejects an invalid %s', (field, value) => {
    expect(studentSchema(false).safeParse({ ...validStudent, [field]: value }).success).toBe(false);
  });
});

describe('enrollment schemas', () => {
  it('treats the roll number as optional but numeric within range', () => {
    const base = { student: { id: 's', label: 'Sam' }, academicYearId: 'y', sectionId: 'x' };
    expect(enrollSchema.safeParse({ ...base, rollNumber: '' }).success).toBe(true);
    expect(enrollSchema.safeParse({ ...base, rollNumber: '12' }).success).toBe(true);
    expect(enrollSchema.safeParse({ ...base, rollNumber: '0' }).success).toBe(false);
    expect(enrollSchema.safeParse({ ...base, rollNumber: '1.5' }).success).toBe(false);
    expect(enrollSchema.safeParse({ ...base, student: null, rollNumber: '' }).success).toBe(false);
    expect(transferSchema.safeParse({ targetSectionId: '', rollNumber: '' }).success).toBe(false);
  });
});

describe('adminSchema', () => {
  it('needs a strong password', () => {
    const base = { firstName: 'Bea', lastName: 'Boss', email: 'bea@school.test', phoneNumber: '' };
    expect(adminSchema.safeParse({ ...base, password: 'short' }).success).toBe(false);
    expect(adminSchema.safeParse({ ...base, password: 'Correct-Pass-1!' }).success).toBe(true);
  });
});

describe('generatePassword', () => {
  it('always satisfies the password rule and differs between calls', () => {
    const passwords = Array.from({ length: 50 }, () => generatePassword());
    expect(passwords.every((value) => newPasswordSchema.safeParse(value).success)).toBe(true);
    expect(new Set(passwords).size).toBe(50);
  });
});
