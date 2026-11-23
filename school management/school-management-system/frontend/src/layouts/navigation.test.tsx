import { screen, waitFor } from '@testing-library/react';
import { makeUser, renderWithProviders } from '../test/renderWithProviders';
import { setViewportWidth } from '../test/viewport';
import type { Role } from '../types/auth';
import { AppRoutes } from '../routes/AppRoutes';
import { navigationByRole } from './navigation';

// Every page loads its data through the shared client; make it fail so each page settles into its error state.
vi.mock('../api/client', () => ({
  apiClient: {
    get: vi.fn().mockRejectedValue(new Error('offline')),
    post: vi.fn().mockRejectedValue(new Error('offline')),
    put: vi.fn().mockRejectedValue(new Error('offline')),
    delete: vi.fn().mockRejectedValue(new Error('offline')),
  },
}));
vi.mock('../hooks/useSystemInfo', () => ({
  useSystemInfo: () => ({
    data: { name: 'x', status: 'UP', profile: 'dev' },
    error: null,
    isPending: false,
    refetch: vi.fn(),
  }),
}));

const ROLE_AUTHORITY: Record<Role, 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT'> = {
  admin: 'ROLE_ADMIN',
  teacher: 'ROLE_TEACHER',
  student: 'ROLE_STUDENT',
};

const links = (Object.keys(navigationByRole) as Role[]).flatMap((role) =>
  navigationByRole[role].groups.flatMap((group) =>
    group.items
      .filter((item) => item.available)
      .map((item) => ({ role, label: item.label, path: item.path })),
  ),
);

describe('navigation', () => {
  beforeEach(() => setViewportWidth(1440));

  it.each(links)(
    '"$label" ($role) opens a real page, not "Page not found"',
    async ({ role, path }) => {
      renderWithProviders(<AppRoutes />, {
        route: `/${role}${path ? `/${path}` : ''}`,
        auth: { user: makeUser({ roles: [ROLE_AUTHORITY[role]] }) },
      });
      await waitFor(() => expect(screen.getAllByRole('heading').length).toBeGreaterThan(0));
      expect(screen.queryByRole('heading', { name: 'Page not found' })).not.toBeInTheDocument();
    },
  );

  it('only offers a link for pages that exist', () => {
    expect(links.length).toBeGreaterThan(30);
  });
});
