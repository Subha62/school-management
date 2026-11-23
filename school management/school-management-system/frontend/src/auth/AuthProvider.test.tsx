import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/errors';
import { makeUser } from '../test/renderWithProviders';
import { AuthProvider } from './AuthProvider';
import { useAuth } from './authContext';

const api = vi.hoisted(() => ({
  fetchCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));
vi.mock('../api/auth', () => api);

import { useState } from 'react';

function Probe() {
  const { user, isLoading, error, login, logout } = useAuth();
  const [logoutError, setLogoutError] = useState<string | null>(null);
  if (isLoading) return <p>loading</p>;
  if (error) return <p>error: {error.message}</p>;
  return (
    <>
      <p>{user ? `signed in as ${user.firstName}` : 'anonymous'}</p>
      {logoutError ? <p>logout failed: {logoutError}</p> : null}
      <button onClick={() => void login({ identifier: 'a', password: 'b' })}>log in</button>
      <button onClick={() => logout().catch((e: ApiError) => setLogoutError(e.message))}>
        log out
      </button>
    </>
  );
}

function renderProvider() {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => Object.values(api).forEach((mock) => mock.mockReset()));

describe('AuthProvider', () => {
  it('treats a 401 from /auth/me as an anonymous visitor, not an error', async () => {
    api.fetchCurrentUser.mockRejectedValue(new ApiError('expired', { status: 401 }));

    renderProvider();

    expect(await screen.findByText('anonymous')).toBeInTheDocument();
  });

  it('loads the signed-in user', async () => {
    api.fetchCurrentUser.mockResolvedValue(makeUser({ firstName: 'Tara' }));

    renderProvider();

    expect(await screen.findByText('signed in as Tara')).toBeInTheDocument();
  });

  it('surfaces other failures such as being offline so the UI does not claim the user is signed out', async () => {
    api.fetchCurrentUser.mockRejectedValue(new ApiError('Unable to reach the server.'));

    renderProvider();

    expect(await screen.findByText(/error: Unable to reach the server/)).toBeInTheDocument();
  });

  it('stores the user after login without another identity request', async () => {
    api.fetchCurrentUser.mockRejectedValue(new ApiError('x', { status: 401 }));
    api.login.mockResolvedValue(makeUser({ firstName: 'Sam' }));
    renderProvider();
    await screen.findByText('anonymous');

    await userEvent.click(screen.getByRole('button', { name: 'log in' }));

    expect(await screen.findByText('signed in as Sam')).toBeInTheDocument();
    expect(api.fetchCurrentUser).toHaveBeenCalledTimes(1);
  });

  it('clears the user and every cached query on logout', async () => {
    api.fetchCurrentUser.mockResolvedValue(makeUser());
    api.logout.mockResolvedValue(undefined);
    const queryClient = renderProvider();
    await screen.findByText('signed in as Sam');
    queryClient.setQueryData(['students', 1], { secret: 'cached student data' });

    await userEvent.click(screen.getByRole('button', { name: 'log out' }));

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument());
    expect(queryClient.getQueryData(['students', 1])).toBeUndefined();
  });

  it('keeps the user signed in and reports the failure when logout cannot reach the server', async () => {
    api.fetchCurrentUser.mockResolvedValue(makeUser());
    api.logout.mockRejectedValue(new ApiError('offline'));
    renderProvider();
    await screen.findByText('signed in as Sam');

    await userEvent.click(screen.getByRole('button', { name: 'log out' }));

    expect(await screen.findByText('logout failed: offline')).toBeInTheDocument();
    expect(screen.getByText('signed in as Sam')).toBeInTheDocument();
  });
});
