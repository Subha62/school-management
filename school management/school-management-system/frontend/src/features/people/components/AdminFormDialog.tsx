import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { InitialPasswordField } from '../../../components/InitialPasswordField';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { adminSchema, blankToNull, type AdminFormValues } from '../../../schemas/people';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useCreateAdmin } from '../hooks';

const FORM_ID = 'admin-form';

export function AdminFormDialog({ onClose }: { onClose: () => void }) {
  const create = useCreateAdmin();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<AdminFormValues>({
    resolver: zodResolver(adminSchema),
    defaultValues: { firstName: '', lastName: '', email: '', phoneNumber: '', password: '' },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await create.mutateAsync({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        phoneNumber: blankToNull(values.phoneNumber),
        password: values.password,
      });
      notify.success('Administrator created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'firstName',
        'lastName',
        'email',
        'phoneNumber',
        'password',
      ]);
      setError(onField ? null : apiError);
    }
  });

  const row = { direction: { xs: 'column', sm: 'row' }, spacing: 2.5 } as const;

  return (
    <ResponsiveDialog
      title="New administrator"
      formId={FORM_ID}
      submitLabel="Create"
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <Alert severity="info">
          Administrators can manage every part of the school. The email address is the sign-in ID.
        </Alert>
        <Stack {...row}>
          <TextField label="First name" fullWidth required {...fieldProps(form, 'firstName')} />
          <TextField label="Last name" fullWidth required {...fieldProps(form, 'lastName')} />
        </Stack>
        <Stack {...row}>
          <TextField label="Email" type="email" fullWidth required {...fieldProps(form, 'email')} />
          <TextField label="Phone" type="tel" fullWidth {...fieldProps(form, 'phoneNumber')} />
        </Stack>
        <InitialPasswordField
          {...fieldProps(form, 'password')}
          onGenerate={(value) =>
            form.setValue('password', value, { shouldDirty: true, shouldValidate: true })
          }
        />
      </Stack>
    </ResponsiveDialog>
  );
}
