import { homePathFor, landingPathFor, primaryRole } from './roles';
import { makeUser } from '../test/renderWithProviders';

describe('landingPathFor', () => {
  const student = makeUser({ roles: ['ROLE_STUDENT'] });

  it('returns to the originally requested page inside the user own workspace', () => {
    expect(landingPathFor(student, '/student/profile')).toBe('/student/profile');
    expect(landingPathFor(student, '/student')).toBe('/student');
  });

  it.each([
    '/admin',
    '/admin/users',
    '//evil.example',
    'https://evil.example',
    'student',
    '/studentX',
  ])('ignores the unsafe or foreign target %s', (target) => {
    expect(landingPathFor(student, target)).toBe('/student');
  });

  it('falls back to the workspace when nothing was requested', () => {
    expect(landingPathFor(student, undefined)).toBe('/student');
  });
});

describe('roles', () => {
  it('maps backend authorities to the highest-privilege workspace', () => {
    expect(primaryRole(makeUser({ roles: ['ROLE_STUDENT', 'ROLE_TEACHER'] }))).toBe('teacher');
    expect(homePathFor(makeUser({ roles: ['ROLE_ADMIN'] }))).toBe('/admin');
  });

  it('sends users without a recognised role to the access-denied page', () => {
    expect(homePathFor(makeUser({ roles: [] }))).toBe('/unauthorized');
  });
});
