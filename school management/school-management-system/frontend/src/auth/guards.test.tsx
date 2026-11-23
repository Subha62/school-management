import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../api/errors';
import { makeUser, renderWithProviders } from '../test/renderWithProviders';
import type { AuthContextValue } from './authContext';
import { HomeRedirect, RequireRole } from './guards';

function renderGuarded(auth: Partial<AuthContextValue>, route = '/admin') {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route
        path="/admin"
        element={
          <RequireRole requiredRole="admin">
            <h1>Admin area</h1>
          </RequireRole>
        }
      />
      <Route path="/teacher" element={<h1>Teacher area</h1>} />
      <Route path="/student" element={<h1>Student area</h1>} />
      <Route path="/login" element={<h1>Login page</h1>} />
      <Route path="/unauthorized" element={<h1>Access denied</h1>} />
    </Routes>,
    { route, auth },
  );
}

describe('RequireRole', () => {
  it('sends anonymous visitors to the login page', () => {
    renderGuarded({ user: null });

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeInTheDocument();
  });

  it('shows the area to a user with the required role', () => {
    renderGuarded({ user: makeUser({ roles: ['ROLE_ADMIN'] }) });

    expect(screen.getByRole('heading', { name: 'Admin area' })).toBeInTheDocument();
  });

  it.each(['ROLE_TEACHER', 'ROLE_STUDENT'] as const)(
    'denies %s access to the admin area',
    (role) => {
      renderGuarded({ user: makeUser({ roles: [role] }) });

      expect(screen.getByRole('heading', { name: 'Access denied' })).toBeInTheDocument();
      expect(screen.queryByRole('heading', { name: 'Admin area' })).not.toBeInTheDocument();
    },
  );

  it('waits for the session check instead of flashing the login page', () => {
    renderGuarded({ user: null, isLoading: true });

    expect(screen.getByRole('progressbar', { name: /checking your session/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Login page' })).not.toBeInTheDocument();
  });

  it('offers a retry when the session cannot be verified', async () => {
    const retry = vi.fn();
    renderGuarded({
      user: null,
      error: new ApiError('Unable to reach the server. Check your connection and try again.'),
      retry,
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to reach the server');
    screen.getByRole('button', { name: 'Retry' }).click();
    expect(retry).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('heading', { name: 'Login page' })).not.toBeInTheDocument();
  });
});

describe('HomeRedirect', () => {
  it('sends anonymous visitors to the login page', () => {
    renderGuarded({ user: null }, '/');

    expect(screen.getByRole('heading', { name: 'Login page' })).toBeInTheDocument();
  });

  it.each([
    ['ROLE_ADMIN', 'Admin area'],
    ['ROLE_TEACHER', 'Teacher area'],
    ['ROLE_STUDENT', 'Student area'],
  ] as const)('sends %s to their own workspace', (role, heading) => {
    renderGuarded({ user: makeUser({ roles: [role] }) }, '/');

    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
  });

  it('prefers the highest privilege for users with several roles', () => {
    renderGuarded({ user: makeUser({ roles: ['ROLE_STUDENT', 'ROLE_ADMIN'] }) }, '/');

    expect(screen.getByRole('heading', { name: 'Admin area' })).toBeInTheDocument();
  });
});
