import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink } from 'react-router-dom';
import { forgotPassword } from '../../api/auth';
import { toApiError, type ApiError } from '../../api/errors';
import { forgotPasswordSchema, type ForgotPasswordFormValues } from '../../schemas/auth';
import { fieldProps } from '../../utils/forms';

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const form = useForm<ForgotPasswordFormValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  });

  const onSubmit = form.handleSubmit(async ({ email }) => {
    setSubmitError(null);
    try {
      await forgotPassword(email);
      setSubmitted(true);
    } catch (error) {
      setSubmitError(toApiError(error));
    }
  });

  if (submitted) {
    return (
      <Stack spacing={2.5}>
        <Typography variant="h5" component="h1">
          Check your email
        </Typography>
        <Alert severity="success">
          If an account exists for that email address, we have sent a link to reset the password.
          The link expires in 30 minutes.
        </Alert>
        <Link component={RouterLink} to="/login">
          Back to sign in
        </Link>
      </Stack>
    );
  }

  return (
    <Stack
      component="form"
      onSubmit={onSubmit}
      noValidate
      spacing={2.5}
      aria-labelledby="forgot-title"
    >
      <Typography id="forgot-title" variant="h5" component="h1">
        Forgot your password?
      </Typography>
      <Typography color="text.secondary">
        Enter the email address of your account and we will send you a reset link.
      </Typography>
      {submitError ? <Alert severity="error">{submitError.message}</Alert> : null}
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        fullWidth
        required
        {...fieldProps(form, 'email')}
      />
      <Button type="submit" variant="contained" size="large" loading={form.formState.isSubmitting}>
        Send reset link
      </Button>
      <Link component={RouterLink} to="/login" sx={{ alignSelf: 'flex-start' }}>
        Back to sign in
      </Link>
    </Stack>
  );
}
