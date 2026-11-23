import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSwitch } from '../../../components/FormSwitch';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { subjectSchema, type SubjectFormValues } from '../../../schemas/academics';
import type { Subject } from '../../../types/academics';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveSubject } from '../hooks';

const FORM_ID = 'subject-form';

export function SubjectFormDialog({
  subject,
  onClose,
}: {
  subject: Subject | null;
  onClose: () => void;
}) {
  const save = useSaveSubject();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<SubjectFormValues>({
    resolver: zodResolver(subjectSchema),
    defaultValues: {
      code: subject?.code ?? '',
      name: subject?.name ?? '',
      description: subject?.description ?? '',
      active: subject?.active ?? true,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({ id: subject?.id, body: { ...values, version: subject?.version } });
      notify.success(subject ? 'Subject updated' : 'Subject created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['code', 'name', 'description']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={subject ? 'Edit subject' : 'New subject'}
      formId={FORM_ID}
      submitLabel={subject ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Code"
          placeholder="MATH"
          fullWidth
          required
          {...fieldProps(form, 'code')}
          helperText={
            form.formState.errors.code?.message ?? 'Unique in the school; stored in capitals'
          }
        />
        <TextField label="Name" fullWidth required {...fieldProps(form, 'name')} />
        <TextField
          label="Description"
          fullWidth
          multiline
          minRows={2}
          {...fieldProps(form, 'description')}
        />
        <FormSwitch control={form.control} name="active" label="Active" />
      </Stack>
    </ResponsiveDialog>
  );
}
