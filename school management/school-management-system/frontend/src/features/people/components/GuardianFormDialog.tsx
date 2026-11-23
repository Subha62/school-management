import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect } from '../../../components/FormSelect';
import { FormSwitch } from '../../../components/FormSwitch';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { blankToNull, guardianSchema, type GuardianFormValues } from '../../../schemas/people';
import type { Guardian, GuardianRelationship } from '../../../types/people';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveGuardian } from '../hooks';
import { RELATIONSHIP_LABELS, toOptions } from '../labels';

const FORM_ID = 'guardian-form';

interface GuardianFormDialogProps {
  /** Null when creating. */
  guardian: Guardian | null;
  onClose: () => void;
  /** Called with the saved guardian, for flows that link it straight away. */
  onSaved?: (guardian: Guardian) => void;
}

export function GuardianFormDialog({ guardian, onClose, onSaved }: GuardianFormDialogProps) {
  const save = useSaveGuardian();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = guardian !== null;
  const form = useForm<GuardianFormValues>({
    resolver: zodResolver(guardianSchema),
    defaultValues: {
      firstName: guardian?.firstName ?? '',
      lastName: guardian?.lastName ?? '',
      relationship: guardian?.relationship ?? 'MOTHER',
      email: guardian?.email ?? '',
      phoneNumber: guardian?.phoneNumber ?? '',
      occupation: guardian?.occupation ?? '',
      address: guardian?.address ?? '',
      emergencyContact: guardian?.emergencyContact ?? false,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const saved = await save.mutateAsync({
        id: guardian?.id,
        body: {
          firstName: values.firstName,
          lastName: values.lastName,
          relationship: values.relationship as GuardianRelationship,
          email: blankToNull(values.email),
          phoneNumber: values.phoneNumber,
          occupation: blankToNull(values.occupation),
          address: blankToNull(values.address),
          emergencyContact: values.emergencyContact,
          version: guardian?.version,
        },
      });
      notify.success(editing ? 'Guardian updated' : 'Guardian created');
      onSaved?.(saved);
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'firstName',
        'lastName',
        'email',
        'phoneNumber',
      ]);
      setError(onField ? null : apiError);
    }
  });

  const row = { direction: { xs: 'column', sm: 'row' }, spacing: 2.5 } as const;

  return (
    <ResponsiveDialog
      title={editing ? 'Edit guardian' : 'New guardian'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <Stack {...row}>
          <TextField label="First name" fullWidth required {...fieldProps(form, 'firstName')} />
          <TextField label="Last name" fullWidth required {...fieldProps(form, 'lastName')} />
        </Stack>
        <FormSelect
          control={form.control}
          name="relationship"
          label="Relationship"
          options={toOptions<GuardianRelationship>(RELATIONSHIP_LABELS)}
          required
        />
        <Stack {...row}>
          <TextField
            label="Phone"
            type="tel"
            fullWidth
            required
            {...fieldProps(form, 'phoneNumber')}
          />
          <TextField label="Email" type="email" fullWidth {...fieldProps(form, 'email')} />
        </Stack>
        <TextField label="Occupation" fullWidth {...fieldProps(form, 'occupation')} />
        <TextField
          label="Address"
          fullWidth
          multiline
          minRows={2}
          {...fieldProps(form, 'address')}
        />
        <FormSwitch control={form.control} name="emergencyContact" label="Emergency contact" />
      </Stack>
    </ResponsiveDialog>
  );
}
