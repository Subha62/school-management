export type Role = 'admin' | 'teacher' | 'student';

export type AuthorityName = 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT';

export interface CurrentUser {
  id: string;
  schoolId: string;
  username: string | null;
  email: string;
  firstName: string;
  lastName: string;
  roles: AuthorityName[];
}

export interface LoginPayload {
  identifier: string;
  password: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}
