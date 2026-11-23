import { screen } from '@testing-library/react';
import { makeUser, renderWithProviders } from '../test/renderWithProviders';
import { setViewportWidth } from '../test/viewport';
import type { AuthContextValue } from '../auth/authContext';
import { AppRoutes } from './AppRoutes';

vi.mock('../hooks/useSystemInfo', () => ({
  useSystemInfo: () => ({
    data: { name: 'school-management-system', status: 'UP', profile: 'dev' },
    error: null,
    isPending: false,
    refetch: vi.fn(),
  }),
}));

vi.mock('../features/announcements/api', () => ({
  getUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
}));

vi.mock('../features/dashboard/api', () => ({
  getAdminDashboard: vi.fn(() => new Promise(() => undefined)),
  getTeacherDashboard: vi.fn(() => new Promise(() => undefined)),
  getStudentDashboard: vi.fn(() => new Promise(() => undefined)),
}));

function renderApp(route: string, auth: Partial<AuthContextValue>) {
  return renderWithProviders(<AppRoutes />, { route, auth });
}

describe('AppRoutes', () => {
  beforeEach(() => setViewportWidth(1440));

  it('sends anonymous visitors from the root to the login page', () => {
    renderApp('/', { user: null });

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('sends a signed-in teacher from the root to the teacher dashboard', () => {
    renderApp('/', { user: makeUser({ roles: ['ROLE_TEACHER'], firstName: 'Tara' }) });

    expect(screen.getByRole('heading', { name: 'Teacher dashboard' })).toBeInTheDocument();
    expect(screen.getByText(/Welcome, Tara/)).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: /teaching navigation/i })).toBeVisible();
  });

  it.each(['/admin', '/teacher', '/admin/profile'])(
    'redirects an anonymous visitor from %s to the login page',
    (route) => {
      renderApp(route, { user: null });

      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    },
  );

  it('keeps a student out of the admin workspace', () => {
    renderApp('/admin', { user: makeUser({ roles: ['ROLE_STUDENT'] }) });

    expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Admin dashboard' })).not.toBeInTheDocument();
  });

  it('shows each role only its own navigation', () => {
    renderApp('/student', { user: makeUser({ roles: ['ROLE_STUDENT'] }) });

    expect(screen.getByText('Report cards')).toBeInTheDocument();
    expect(screen.queryByText('Audit logs')).not.toBeInTheDocument();
    expect(screen.queryByText('Submission review')).not.toBeInTheDocument();
  });

  it('renders the profile page with the signed-in identity', () => {
    renderApp('/student/profile', {
      user: makeUser({ firstName: 'Sam', lastName: 'Student', email: 'sam@school.test' }),
    });

    expect(screen.getByRole('heading', { name: 'My profile' })).toBeInTheDocument();
    expect(screen.getByText('Sam Student')).toBeInTheDocument();
    expect(screen.getByText('sam@school.test')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Change password' })).toHaveAttribute(
      'href',
      '/student/change-password',
    );
  });

  it('renders the change-password page inside the workspace layout', () => {
    renderApp('/teacher/change-password', { user: makeUser({ roles: ['ROLE_TEACHER'] }) });

    expect(screen.getByRole('heading', { name: 'Change password' })).toBeInTheDocument();
    expect(screen.getByLabelText(/current password/i)).toBeInTheDocument();
  });

  it.each(['/missing', '/admin/unknown'])('shows the not found page for %s', (route) => {
    renderApp(route, { user: makeUser({ roles: ['ROLE_ADMIN'] }) });

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('renders the recovery pages without a session', () => {
    renderApp('/forgot-password', { user: null });

    expect(screen.getByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument();
  });
});
