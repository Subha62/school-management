import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../../api/auth';
import { toApiError, type ApiError } from '../../api/errors';
import { PasswordField } from '../../components/PasswordField';
import { resetPasswordSchema, type ResetPasswordFormValues } from '../../schemas/auth';
import { applyServerFieldErrors, fieldProps } from '../../utils/forms';

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  if (!token) {
    return (
      <Stack spacing={2.5}>
        <Typography variant="h5" component="h1">
          Reset password
        </Typography>
        <Alert severity="error">
          This reset link is incomplete. Request a new link and use the one in the latest email.
        </Alert>
        <Link component={RouterLink} to="/forgot-password">
          Request a new link
        </Link>
      </Stack>
    );
  }

  const onSubmit = form.handleSubmit(async ({ newPassword }) => {
    setSubmitError(null);
    try {
      await resetPassword({ token, newPassword });
      navigate('/login', {
        replace: true,
        state: { message: 'Your password has been updated. Sign in with your new password.' },
      });
    } catch (error) {
      const apiError = toApiError(error);
      const shownOnField = applyServerFieldErrors(form, apiError, ['newPassword']);
      setSubmitError(shownOnField ? null : apiError);
    }
  });

  return (
    <Stack
      component="form"
      onSubmit={onSubmit}
      noValidate
      spacing={2.5}
      aria-labelledby="reset-title"
    >
      <Typography id="reset-title" variant="h5" component="h1">
        Choose a new password
      </Typography>
      {submitError ? (
        <Alert
          severity="error"
          action={
            submitError.status === 400 ? (
              <Button color="inherit" size="small" component={RouterLink} to="/forgot-password">
                New link
              </Button>
            ) : undefined
          }
        >
          {submitError.message}
        </Alert>
      ) : null}
      <PasswordField
        label="New password"
        autoComplete="new-password"
        required
        {...fieldProps(form, 'newPassword')}
      />
      <PasswordField
        label="Confirm new password"
        autoComplete="new-password"
        required
        {...fieldProps(form, 'confirmPassword')}
      />
      <Button type="submit" variant="contained" size="large" loading={form.formState.isSubmitting}>
        Update password
      </Button>
    </Stack>
  );
}
