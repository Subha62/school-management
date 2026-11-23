import type { AuthorityName, CurrentUser, Role } from '../types/auth';

const ROLE_BY_AUTHORITY: Record<AuthorityName, Role> = {
  ROLE_ADMIN: 'admin',
  ROLE_TEACHER: 'teacher',
  ROLE_STUDENT: 'student',
};

/** Highest privilege first: decides the workspace a multi-role user lands in. */
const ROLE_PRIORITY: Role[] = ['admin', 'teacher', 'student'];

export const ROLE_BASE_PATH: Record<Role, string> = {
  admin: '/admin',
  teacher: '/teacher',
  student: '/student',
};

export function rolesOf(user: CurrentUser): Role[] {
  return user.roles.flatMap((authority) => {
    const role = ROLE_BY_AUTHORITY[authority];
    return role ? [role] : [];
  });
}

export function hasRole(user: CurrentUser, role: Role): boolean {
  return rolesOf(user).includes(role);
}

export function primaryRole(user: CurrentUser): Role | null {
  const roles = rolesOf(user);
  return ROLE_PRIORITY.find((role) => roles.includes(role)) ?? null;
}

/** Where a signed-in user starts: their workspace, or the access-denied page when no role is recognised. */
export function homePathFor(user: CurrentUser): string {
  const role = primaryRole(user);
  return role ? ROLE_BASE_PATH[role] : '/unauthorized';
}

export function fullName(user: CurrentUser): string {
  return `${user.firstName} ${user.lastName}`.trim();
}

/**
 * Where to go after signing in. The originally requested path is honoured only when it belongs to the user's
 * own workspace, which also rules out open redirects to other origins.
 */
export function landingPathFor(user: CurrentUser, requested: string | undefined): string {
  const home = homePathFor(user);
  if (!requested || !requested.startsWith('/') || requested.startsWith('//')) {
    return home;
  }
  const inOwnWorkspace = requested === home || requested.startsWith(`${home}/`);
  return inOwnWorkspace ? requested : home;
}
