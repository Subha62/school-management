import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { changePassword } from '../../api/auth';
import { toApiError, type ApiError } from '../../api/errors';
import { PageHeader } from '../../components/PageHeader';
import { PasswordField } from '../../components/PasswordField';
import { changePasswordSchema, type ChangePasswordFormValues } from '../../schemas/auth';
import { applyServerFieldErrors, fieldProps } from '../../utils/forms';

const EMPTY_VALUES: ChangePasswordFormValues = {
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
};

export function ChangePasswordPage() {
  const [success, setSuccess] = useState(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const form = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: EMPTY_VALUES,
  });

  const onSubmit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    setSubmitError(null);
    setSuccess(false);
    try {
      await changePassword({ currentPassword, newPassword });
      form.reset(EMPTY_VALUES);
      setSuccess(true);
    } catch (error) {
      const apiError = toApiError(error);
      // A wrong current password comes back as a business-rule problem rather than a field error.
      if (apiError.status === 422 && /current password/i.test(apiError.message)) {
        form.setError('currentPassword', { type: 'server', message: apiError.message });
        return;
      }
      const shownOnField = applyServerFieldErrors(form, apiError, [
        'newPassword',
        'currentPassword',
      ]);
      setSubmitError(shownOnField ? null : apiError);
    }
  });

  return (
    <>
      <PageHeader
        title="Change password"
        subtitle="Other devices will be signed out after you change it."
      />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, maxWidth: 480 }}>
        <Stack
          component="form"
          onSubmit={onSubmit}
          noValidate
          spacing={2.5}
          aria-label="Change password"
        >
          {success ? <Alert severity="success">Your password has been changed.</Alert> : null}
          {submitError ? <Alert severity="error">{submitError.message}</Alert> : null}
          <PasswordField
            label="Current password"
            autoComplete="current-password"
            required
            {...fieldProps(form, 'currentPassword')}
          />
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
          <Button
            type="submit"
            variant="contained"
            size="large"
            loading={form.formState.isSubmitting}
          >
            Change password
          </Button>
        </Stack>
      </Paper>
    </>
  );
}
