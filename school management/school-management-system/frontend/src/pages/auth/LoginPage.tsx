import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom';
import { toApiError, type ApiError } from '../../api/errors';
import { useAuth } from '../../auth/authContext';
import { landingPathFor } from '../../auth/roles';
import { LoadingState } from '../../components/LoadingState';
import { PasswordField } from '../../components/PasswordField';
import { loginSchema, type LoginFormValues } from '../../schemas/auth';
import { fieldProps } from '../../utils/forms';

interface LoginLocationState {
  from?: string;
  message?: string;
}

const INCORRECT_CREDENTIALS =
  'The email/ID or password is incorrect, or the account is temporarily unavailable.';

export function LoginPage() {
  const { user, isLoading, login } = useAuth();
  const location = useLocation();
  const state = (location.state as LoginLocationState | null) ?? {};
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: '', password: '' },
  });

  if (isLoading) {
    return <LoadingState label="Checking your session" />;
  }
  if (user) {
    return <Navigate to={landingPathFor(user, state.from)} replace />;
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await login(values);
    } catch (error) {
      const apiError = toApiError(error);
      form.resetField('password');
      setSubmitError(apiError);
    }
  });

  const submitMessage = submitError
    ? submitError.status === 401
      ? INCORRECT_CREDENTIALS
      : submitError.message
    : null;

  return (
    <Stack
      component="form"
      onSubmit={onSubmit}
      noValidate
      spacing={2.5}
      aria-labelledby="login-title"
    >
      <Typography id="login-title" variant="h5" component="h1">
        Sign in
      </Typography>
      {state.message ? <Alert severity="success">{state.message}</Alert> : null}
      {submitMessage ? <Alert severity="error">{submitMessage}</Alert> : null}
      <TextField
        label="Email or ID"
        autoComplete="username"
        fullWidth
        required
        {...fieldProps(form, 'identifier')}
      />
      <PasswordField
        label="Password"
        autoComplete="current-password"
        required
        {...fieldProps(form, 'password')}
      />
      <Button type="submit" variant="contained" size="large" loading={form.formState.isSubmitting}>
        Sign in
      </Button>
      <Link component={RouterLink} to="/forgot-password" sx={{ alignSelf: 'flex-start' }}>
        Forgot your password?
      </Link>
    </Stack>
  );
}
