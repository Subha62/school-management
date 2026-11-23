import type { NamedRef } from './academics';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'WITHDRAWN' | 'GRADUATED';
export type EmploymentStatus = 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED' | 'RETIRED';
export type EnrollmentStatus = 'ACTIVE' | 'TRANSFERRED' | 'PROMOTED' | 'WITHDRAWN' | 'COMPLETED';
export type GuardianRelationship =
  'FATHER' | 'MOTHER' | 'GUARDIAN' | 'GRANDPARENT' | 'SIBLING' | 'OTHER';
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'ARCHIVED';
export type RoleName = 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT';

export interface EnrollmentRef {
  enrollmentId: string;
  academicYear: string;
  grade: string;
  section: string;
  rollNumber: number | null;
}

export interface Student {
  id: string;
  userId: string;
  admissionNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  dateOfBirth: string;
  gender: Gender;
  admissionDate: string;
  bloodGroup: string | null;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  state: string | null;
  postalCode: string | null;
  emergencyContactName: string;
  emergencyContactPhone: string;
  status: StudentStatus;
  currentEnrollment: EnrollmentRef | null;
  version: number;
}

/**
 * `admissionNumber` and `password` apply when creating; `status` and `version` when updating. Optional text is
 * sent as null when left blank, because the API validates the format of any text it receives.
 */
export interface StudentInput {
  admissionNumber?: string;
  password?: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  dateOfBirth: string;
  gender: Gender;
  admissionDate: string;
  bloodGroup: string | null;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  state: string | null;
  postalCode: string | null;
  emergencyContactName: string;
  emergencyContactPhone: string;
  status?: StudentStatus;
  version?: number;
}

export interface Teacher {
  id: string;
  userId: string;
  employeeId: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  joiningDate: string;
  qualification: string | null;
  specialization: string | null;
  employmentStatus: EmploymentStatus;
  accountStatus: AccountStatus;
  version: number;
}

export interface TeacherInput {
  employeeId?: string;
  password?: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  joiningDate: string;
  qualification: string | null;
  specialization: string | null;
  employmentStatus: EmploymentStatus;
  version?: number;
}

export interface Guardian {
  id: string;
  firstName: string;
  lastName: string;
  relationship: GuardianRelationship;
  email: string | null;
  phoneNumber: string;
  occupation: string | null;
  address: string | null;
  emergencyContact: boolean;
  studentCount: number;
  version: number;
}

export interface GuardianInput {
  firstName: string;
  lastName: string;
  relationship: GuardianRelationship;
  email: string | null;
  phoneNumber: string;
  occupation: string | null;
  address: string | null;
  emergencyContact: boolean;
  version?: number;
}

export interface StudentGuardianLink {
  id: string;
  guardianId: string;
  firstName: string;
  lastName: string;
  relationship: GuardianRelationship;
  phoneNumber: string;
  email: string | null;
  primaryGuardian: boolean;
  receivesNotifications: boolean;
  authorizedPickup: boolean;
  version: number;
}

export interface GuardianLinkInput {
  guardianId?: string;
  primaryGuardian: boolean;
  receivesNotifications: boolean;
  authorizedPickup: boolean;
  version?: number;
}

export interface EnrollmentStudent {
  id: string;
  admissionNumber: string;
  name: string;
}

export interface Enrollment {
  id: string;
  student: EnrollmentStudent;
  academicYear: NamedRef;
  gradeLevel: NamedRef;
  section: NamedRef;
  rollNumber: number | null;
  enrollmentDate: string;
  status: EnrollmentStatus;
  sourceEnrollmentId: string | null;
  promotionBatchId: string | null;
  version: number;
}

export interface EnrollInput {
  studentId: string;
  sectionId: string;
  rollNumber: number | null;
}

export interface TransferInput {
  targetSectionId: string;
  rollNumber: number | null;
}

export interface PromotionItemInput {
  studentId: string;
  targetSectionId: string;
}

export interface PromotionInput {
  sourceAcademicYearId: string;
  targetAcademicYearId: string;
  items: PromotionItemInput[];
}

export interface PromotionResult {
  studentId: string;
  admissionNumber: string | null;
  studentName: string | null;
  outcome: 'PROMOTED' | 'FAILED';
  reason: string | null;
  newEnrollmentId: string | null;
  rollNumber: number | null;
}

export interface PromotionSummary {
  batchId: string;
  sourceAcademicYear: string;
  targetAcademicYear: string;
  performedBy: string;
  performedAt: string;
  total: number;
  promoted: number;
  failed: number;
  results: PromotionResult[];
}

export interface PromotionBatch {
  id: string;
  sourceAcademicYear: string;
  targetAcademicYear: string;
  performedBy: string;
  performedAt: string;
  total: number;
  promoted: number;
  failed: number;
}

export interface AdminUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string | null;
  roles: RoleName[];
  accountStatus: AccountStatus;
  temporarilyLocked: boolean;
  lastLoginAt: string | null;
  version: number;
}

export interface AdminInput {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string | null;
  password: string;
}
