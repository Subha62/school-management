import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { makeUser, renderWithProviders } from '../test/renderWithProviders';
import { setViewportWidth } from '../test/viewport';
import { RoleLayout } from './RoleLayout';
import type { Role } from './navigation';

vi.mock('../hooks/useSystemInfo', () => ({
  useSystemInfo: () => ({ data: undefined, error: null, isPending: true, refetch: vi.fn() }),
}));

const announcementsApi = vi.hoisted(() => ({ getUnreadCount: vi.fn() }));
vi.mock('../features/announcements/api', () => announcementsApi);

beforeEach(() => {
  announcementsApi.getUnreadCount.mockReset();
  announcementsApi.getUnreadCount.mockResolvedValue({ count: 0 });
});

const AUTHORITY = {
  admin: 'ROLE_ADMIN',
  teacher: 'ROLE_TEACHER',
  student: 'ROLE_STUDENT',
} as const;

function renderLayout(role: Role, width: number) {
  setViewportWidth(width);
  return renderWithProviders(
    <Routes>
      <Route path={`/${role}`} element={<RoleLayout userRole={role} />}>
        <Route index element={<h1>Dashboard content</h1>} />
      </Route>
      <Route path="/login" element={<h1>Login page</h1>} />
    </Routes>,
    { route: `/${role}`, auth: { user: makeUser({ roles: [AUTHORITY[role]] }) } },
  );
}

describe('RoleLayout', () => {
  it('shows a permanent navigation drawer without a menu button on desktop', () => {
    renderLayout('admin', 1440);

    expect(screen.getByRole('navigation', { name: /administration navigation/i })).toBeVisible();
    expect(screen.queryByRole('button', { name: /open navigation menu/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
  });

  it('hides navigation behind a menu button on mobile and closes it after navigating', async () => {
    const user = userEvent.setup();
    renderLayout('student', 375);

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /open navigation menu/i }));
    const navigation = await screen.findByRole('navigation', { name: /student navigation/i });
    await user.click(within(navigation).getByRole('link', { name: 'Dashboard' }));

    await waitFor(() => expect(screen.queryByRole('navigation')).not.toBeInTheDocument());
  });

  it('only lists the destinations of the selected role', () => {
    renderLayout('teacher', 1440);

    expect(screen.getByText('Submission review')).toBeInTheDocument();
    expect(screen.queryByText('Audit logs')).not.toBeInTheDocument();
    expect(screen.queryByText('Fees')).not.toBeInTheDocument();
  });

  it('does not offer links to features that are not built yet', () => {
    renderLayout('teacher', 1440);

    expect(screen.queryByRole('link', { name: /student list/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Attendance' })).toHaveAttribute(
      'href',
      '/teacher/attendance',
    );
    expect(screen.getAllByText('Coming soon').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Profile' })).toHaveAttribute(
      'href',
      '/teacher/profile',
    );
  });

  it('links the administrator to the audit logs', () => {
    renderLayout('admin', 1440);

    expect(screen.getByRole('link', { name: 'Audit logs' })).toHaveAttribute(
      'href',
      '/admin/audit-logs',
    );
    expect(screen.getByRole('link', { name: 'Students' })).toHaveAttribute(
      'href',
      '/admin/students',
    );
  });

  it('shows the number of unread announcements beside the Announcements link', async () => {
    announcementsApi.getUnreadCount.mockResolvedValue({ count: 3 });
    renderLayout('student', 1440);

    expect(await screen.findByRole('link', { name: 'Announcements 3 unread' })).toHaveAttribute(
      'href',
      '/student/announcements',
    );
  });

  it('shows the signed-in user in the account menu with profile and password links', async () => {
    const user = userEvent.setup();
    renderLayout('student', 1440);

    await user.click(screen.getByRole('button', { name: 'Account menu' }));

    expect(await screen.findByText('Sam Student')).toBeInTheDocument();
    expect(screen.getByText('sam@school.test')).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Profile' })).toHaveAttribute(
      'href',
      '/student/profile',
    );
    expect(screen.getByRole('menuitem', { name: 'Change password' })).toHaveAttribute(
      'href',
      '/student/change-password',
    );
  });

  it('signs out and returns to the login page', async () => {
    const user = userEvent.setup();
    const { authValue } = renderLayout('teacher', 1440);

    await user.click(screen.getByRole('button', { name: 'Account menu' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Sign out' }));

    expect(authValue.logout).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole('heading', { name: 'Login page' })).toBeInTheDocument();
  });
});
