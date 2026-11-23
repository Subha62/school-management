import type {
  AccountStatus,
  EmploymentStatus,
  EnrollmentStatus,
  Gender,
  GuardianRelationship,
  RoleName,
  StudentStatus,
} from '../../types/people';

export const GENDER_LABELS: Record<Gender, string> = {
  MALE: 'Male',
  FEMALE: 'Female',
  OTHER: 'Other',
};

export const STUDENT_STATUS_LABELS: Record<StudentStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  WITHDRAWN: 'Withdrawn',
  GRADUATED: 'Graduated',
};

export const EMPLOYMENT_LABELS: Record<EmploymentStatus, string> = {
  ACTIVE: 'Active',
  ON_LEAVE: 'On leave',
  RESIGNED: 'Resigned',
  RETIRED: 'Retired',
};

export const ENROLLMENT_LABELS: Record<EnrollmentStatus, string> = {
  ACTIVE: 'Active',
  TRANSFERRED: 'Transferred',
  PROMOTED: 'Promoted',
  WITHDRAWN: 'Withdrawn',
  COMPLETED: 'Completed',
};

export const RELATIONSHIP_LABELS: Record<GuardianRelationship, string> = {
  FATHER: 'Father',
  MOTHER: 'Mother',
  GUARDIAN: 'Guardian',
  GRANDPARENT: 'Grandparent',
  SIBLING: 'Sibling',
  OTHER: 'Other',
};

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  LOCKED: 'Locked',
  SUSPENDED: 'Suspended',
  ARCHIVED: 'Archived',
};

export const ROLE_LABELS: Record<RoleName, string> = {
  ROLE_ADMIN: 'Administrator',
  ROLE_TEACHER: 'Teacher',
  ROLE_STUDENT: 'Student',
};

export function toOptions<K extends string>(labels: Record<K, string>) {
  return (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }));
}

export const fullName = (person: { firstName: string; lastName: string }) =>
  `${person.firstName} ${person.lastName}`.trim();

export const studentTone = (status: StudentStatus) =>
  status === 'ACTIVE' ? 'success' : status === 'GRADUATED' ? 'info' : 'default';

export const enrollmentTone = (status: EnrollmentStatus) =>
  status === 'ACTIVE'
    ? 'success'
    : status === 'PROMOTED' || status === 'COMPLETED'
      ? 'info'
      : 'default';
