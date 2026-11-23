import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import type { AuthContextValue } from '../../auth/authContext';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { LoginPage } from './LoginPage';

function renderLogin(auth: Partial<AuthContextValue> = {}) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/student" element={<h1>Student area</h1>} />
    </Routes>,
    { route: '/login', auth: { user: null, ...auth } },
  );
}

async function fillAndSubmit(identifier: string, password: string) {
  await userEvent.type(screen.getByLabelText(/email or id/i), identifier);
  await userEvent.type(screen.getByLabelText(/^password/i), password);
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('shows field-level validation messages and does not call the API for an empty form', async () => {
    const login = vi.fn();
    renderLogin({ login });

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Email or ID is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('submits the trimmed identifier and the password', async () => {
    const login = vi.fn().mockResolvedValue(makeUser());
    renderLogin({ login });

    await fillAndSubmit('  S-0001 ', 'Secret-Pass-1!');

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith({ identifier: 'S-0001', password: 'Secret-Pass-1!' }),
    );
  });

  it('shows one generic message for rejected credentials and clears the password', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError('Your session has expired.', { status: 401 }));
    renderLogin({ login });

    await fillAndSubmit('ghost@school.test', 'Wrong-Pass-1!');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/email\/ID or password is incorrect/i);
    expect(alert).not.toHaveTextContent(/expired/i);
    expect(screen.getByLabelText(/^password/i)).toHaveValue('');
    expect(screen.getByLabelText(/email or id/i)).toHaveValue('ghost@school.test');
  });

  it('shows the throttling message from the server when too many attempts were made', async () => {
    const login = vi
      .fn()
      .mockRejectedValue(new ApiError('Too many attempts. Try again later.', { status: 429 }));
    renderLogin({ login });

    await fillAndSubmit('a@school.test', 'x');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Try again later.',
    );
  });

  it('reveals and hides the password on request', async () => {
    renderLogin();
    const password = screen.getByLabelText(/^password/i);
    expect(password).toHaveAttribute('type', 'password');

    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));

    expect(password).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide password' })).toBeInTheDocument();
  });

  it('links to password recovery', () => {
    renderLogin();

    expect(screen.getByRole('link', { name: /forgot your password/i })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });

  it('waits for the session check before showing the form', () => {
    renderLogin({ isLoading: true });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('sends an already signed-in user to their workspace', () => {
    renderLogin({ user: makeUser({ roles: ['ROLE_STUDENT'] }) });

    expect(screen.getByRole('heading', { name: 'Student area' })).toBeInTheDocument();
  });
});
