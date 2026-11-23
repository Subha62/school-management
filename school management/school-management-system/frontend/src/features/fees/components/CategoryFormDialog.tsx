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
import { blankToNull } from '../../../schemas/people';
import { categorySchema, type CategoryFormValues } from '../../../schemas/fees';
import type { FeeCategory } from '../../../types/fees';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveCategory } from '../hooks';

const FORM_ID = 'fee-category-form';

export function CategoryFormDialog({
  category,
  onClose,
}: {
  category: FeeCategory | null;
  onClose: () => void;
}) {
  const save = useSaveCategory();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = category !== null;
  const form = useForm<CategoryFormValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: {
      name: category?.name ?? '',
      description: category?.description ?? '',
      active: category?.active ?? true,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: category?.id,
        body: {
          name: values.name,
          description: blankToNull(values.description),
          active: values.active,
          version: category?.version,
        },
      });
      notify.success(editing ? 'Fee category updated' : 'Fee category created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['name', 'description']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit fee category' : 'New fee category'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create category'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Name"
          placeholder="Tuition"
          fullWidth
          required
          {...fieldProps(form, 'name')}
        />
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
