import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { renderWithProviders } from '../../test/renderWithProviders';
import { ChangePasswordPage } from './ChangePasswordPage';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { ResetPasswordPage } from './ResetPasswordPage';

const api = vi.hoisted(() => ({
  changePassword: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
}));
vi.mock('../../api/auth', () => api);

beforeEach(() => {
  Object.values(api).forEach((mock) => mock.mockReset());
});

const STRONG = 'Brand-New-Pass9!';

describe('ChangePasswordPage', () => {
  async function fill(current: string, next: string, confirm: string) {
    await userEvent.type(screen.getByLabelText(/current password/i), current);
    await userEvent.type(screen.getByLabelText(/^new password/i), next);
    await userEvent.type(screen.getByLabelText(/confirm new password/i), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
  }

  it('validates the new password and the confirmation before calling the API', async () => {
    renderWithProviders(<ChangePasswordPage />);

    await fill('Old-Pass-1!', 'weak', 'different');

    expect(await screen.findByText(/at least 10 characters/i)).toBeInTheDocument();
    expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
    expect(api.changePassword).not.toHaveBeenCalled();
  });

  it('changes the password, confirms success and empties the form', async () => {
    api.changePassword.mockResolvedValue(undefined);
    renderWithProviders(<ChangePasswordPage />);

    await fill('Old-Pass-1!', STRONG, STRONG);

    expect(await screen.findByText('Your password has been changed.')).toBeInTheDocument();
    expect(api.changePassword).toHaveBeenCalledWith({
      currentPassword: 'Old-Pass-1!',
      newPassword: STRONG,
    });
    expect(screen.getByLabelText(/current password/i)).toHaveValue('');
  });

  it('shows a wrong current password next to that field', async () => {
    api.changePassword.mockRejectedValue(
      new ApiError('The current password is incorrect.', { status: 422 }),
    );
    renderWithProviders(<ChangePasswordPage />);

    await fill('Wrong-Pass-1!', STRONG, STRONG);

    expect(await screen.findByText('The current password is incorrect.')).toBeInTheDocument();
    expect(screen.getByLabelText(/current password/i)).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows server-side validation errors on the new password field', async () => {
    api.changePassword.mockRejectedValue(
      new ApiError('Validation failed', {
        status: 400,
        fieldErrors: { newPassword: 'Password does not meet the policy' },
      }),
    );
    renderWithProviders(<ChangePasswordPage />);

    await fill('Old-Pass-1!', STRONG, STRONG);

    expect(await screen.findByText('Password does not meet the policy')).toBeInTheDocument();
  });

  it('shows throttling errors as a general message', async () => {
    api.changePassword.mockRejectedValue(
      new ApiError('Too many attempts. Try again later.', { status: 429 }),
    );
    renderWithProviders(<ChangePasswordPage />);

    await fill('Old-Pass-1!', STRONG, STRONG);

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts');
  });
});

describe('ForgotPasswordPage', () => {
  it('rejects a malformed email without calling the API', async () => {
    renderWithProviders(<ForgotPasswordPage />);

    await userEvent.type(screen.getByLabelText(/^email/i), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(api.forgotPassword).not.toHaveBeenCalled();
  });

  it('always shows the same neutral confirmation', async () => {
    api.forgotPassword.mockResolvedValue(undefined);
    renderWithProviders(<ForgotPasswordPage />);

    await userEvent.type(screen.getByLabelText(/^email/i), 'anyone@school.test');
    await userEvent.click(screen.getByRole('button', { name: 'Send reset link' }));

    expect(
      await screen.findByText(/if an account exists for that email address/i),
    ).toBeInTheDocument();
    expect(api.forgotPassword).toHaveBeenCalledWith('anyone@school.test');
  });
});

describe('ResetPasswordPage', () => {
  function renderReset(route: string) {
    return renderWithProviders(
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/login" element={<h1>Login page</h1>} />
      </Routes>,
      { route },
    );
  }

  it('explains when the link has no token and offers a new one', () => {
    renderReset('/reset-password');

    expect(screen.getByRole('alert')).toHaveTextContent(/incomplete/i);
    expect(screen.getByRole('link', { name: /request a new link/i })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });

  it('sets the new password with the token from the link and returns to sign in', async () => {
    api.resetPassword.mockResolvedValue(undefined);
    renderReset('/reset-password?token=abc123');

    await userEvent.type(screen.getByLabelText(/^new password/i), STRONG);
    await userEvent.type(screen.getByLabelText(/confirm new password/i), STRONG);
    await userEvent.click(screen.getByRole('button', { name: 'Update password' }));

    await waitFor(() =>
      expect(api.resetPassword).toHaveBeenCalledWith({ token: 'abc123', newPassword: STRONG }),
    );
    expect(await screen.findByRole('heading', { name: 'Login page' })).toBeInTheDocument();
  });

  it('shows an invalid or expired link and offers to request another', async () => {
    api.resetPassword.mockRejectedValue(
      new ApiError('This password reset link is invalid or has expired.', { status: 400 }),
    );
    renderReset('/reset-password?token=stale');

    await userEvent.type(screen.getByLabelText(/^new password/i), STRONG);
    await userEvent.type(screen.getByLabelText(/confirm new password/i), STRONG);
    await userEvent.click(screen.getByRole('button', { name: 'Update password' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/i);
    expect(screen.getByRole('link', { name: 'New link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });
});
