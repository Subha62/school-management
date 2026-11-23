import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ChoiceAutocomplete } from '../../../components/ChoiceAutocomplete';
import { FormSwitch } from '../../../components/FormSwitch';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { guardianLinkSchema, type GuardianLinkFormValues } from '../../../schemas/people';
import type { StudentGuardianLink } from '../../../types/people';
import { useGuardianChoices, useSaveGuardianLink } from '../hooks';
import { fullName } from '../labels';

const FORM_ID = 'guardian-link-form';

interface GuardianLinkDialogProps {
  studentId: string;
  /** Null when linking a new guardian; the guardian of an existing link cannot be changed. */
  link: StudentGuardianLink | null;
  onClose: () => void;
}

export function GuardianLinkDialog({ studentId, link, onClose }: GuardianLinkDialogProps) {
  const save = useSaveGuardianLink();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = link !== null;
  const schema = useMemo(() => guardianLinkSchema(!editing), [editing]);
  const form = useForm<GuardianLinkFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      guardian: link ? { id: link.guardianId, label: fullName(link) } : null,
      primaryGuardian: link?.primaryGuardian ?? false,
      receivesNotifications: link?.receivesNotifications ?? true,
      authorizedPickup: link?.authorizedPickup ?? false,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        studentId,
        linkId: link?.id,
        body: {
          guardianId: values.guardian?.id,
          primaryGuardian: values.primaryGuardian,
          receivesNotifications: values.receivesNotifications,
          authorizedPickup: values.authorizedPickup,
          version: link?.version,
        },
      });
      notify.success(editing ? 'Guardian link updated' : 'Guardian linked');
      onClose();
    } catch (cause) {
      setError(toApiError(cause));
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit guardian link' : 'Link guardian'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Link'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <ChoiceAutocomplete
          control={form.control}
          name="guardian"
          label="Guardian"
          required
          disabled={editing}
          noOptionsText="No matching guardians. Create the guardian first."
          useOptions={useGuardianChoices}
        />
        <FormSwitch control={form.control} name="primaryGuardian" label="Primary guardian" />
        <FormSwitch
          control={form.control}
          name="receivesNotifications"
          label="Receives notifications"
        />
        <FormSwitch control={form.control} name="authorizedPickup" label="Authorized for pickup" />
      </Stack>
    </ResponsiveDialog>
  );
}
