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
import { gradeSchema, type GradeFormValues } from '../../../schemas/academics';
import type { GradeLevel } from '../../../types/academics';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveGrade } from '../hooks';

const FORM_ID = 'grade-form';

export function GradeFormDialog({
  grade,
  onClose,
}: {
  grade: GradeLevel | null;
  onClose: () => void;
}) {
  const save = useSaveGrade();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<GradeFormValues>({
    resolver: zodResolver(gradeSchema),
    defaultValues: {
      name: grade?.name ?? '',
      displayOrder: String(grade?.displayOrder ?? 0),
      active: grade?.active ?? true,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: grade?.id,
        body: {
          name: values.name,
          displayOrder: Number(values.displayOrder),
          active: values.active,
          version: grade?.version,
        },
      });
      notify.success(grade ? 'Grade updated' : 'Grade created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['name', 'displayOrder']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={grade ? 'Edit grade' : 'New grade'}
      formId={FORM_ID}
      submitLabel={grade ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Name"
          placeholder="Grade 8"
          fullWidth
          required
          {...fieldProps(form, 'name')}
        />
        <TextField
          label="Display order"
          fullWidth
          required
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          {...fieldProps(form, 'displayOrder')}
          helperText={
            form.formState.errors.displayOrder?.message ?? 'Lower numbers are listed first'
          }
        />
        <FormSwitch control={form.control} name="active" label="Active" />
      </Stack>
    </ResponsiveDialog>
  );
}
