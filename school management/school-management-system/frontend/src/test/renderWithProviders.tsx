import { ThemeProvider } from '@mui/material/styles';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { NotificationProvider } from '../app/NotificationProvider';
import { theme } from '../app/theme';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import type { CurrentUser } from '../types/auth';

export function makeUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: 'user-1',
    schoolId: 'school-1',
    username: 'S-0001',
    email: 'sam@school.test',
    firstName: 'Sam',
    lastName: 'Student',
    roles: ['ROLE_STUDENT'],
    ...overrides,
  };
}

interface RenderOptions {
  route?: string;
  /** Provide a fake auth state. Omit to render without an AuthContext (for components that need none). */
  auth?: Partial<AuthContextValue>;
}

export function renderWithProviders(ui: ReactElement, { route = '/', auth }: RenderOptions = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const authValue: AuthContextValue = {
    user: null,
    isLoading: false,
    error: null,
    retry: vi.fn(),
    login: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
    ...auth,
  };
  const content = auth ? <AuthContext.Provider value={authValue}>{ui}</AuthContext.Provider> : ui;
  const result = render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <NotificationProvider>{content}</NotificationProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return { ...result, authValue };
}
