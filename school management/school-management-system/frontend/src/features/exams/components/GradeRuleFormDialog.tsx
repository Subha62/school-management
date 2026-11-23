import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { gradeRuleSchema, type GradeRuleFormValues } from '../../../schemas/exams';
import type { GradeRule } from '../../../types/exams';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveGradeRule } from '../hooks';

const FORM_ID = 'grade-rule-form';

export function GradeRuleFormDialog({
  rule,
  onClose,
}: {
  rule: GradeRule | null;
  onClose: () => void;
}) {
  const save = useSaveGradeRule();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<GradeRuleFormValues>({
    resolver: zodResolver(gradeRuleSchema),
    defaultValues: {
      grade: rule?.grade ?? '',
      description: rule?.description ?? '',
      minPercent: rule ? String(rule.minPercent) : '',
      maxPercent: rule ? String(rule.maxPercent) : '',
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: rule?.id,
        body: {
          grade: values.grade,
          description: values.description === '' ? null : values.description,
          minPercent: Number(values.minPercent),
          maxPercent: Number(values.maxPercent),
          version: rule?.version,
        },
      });
      notify.success(rule ? 'Grade band updated' : 'Grade band created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'grade',
        'description',
        'minPercent',
        'maxPercent',
      ]);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={rule ? 'Edit grade band' : 'New grade band'}
      formId={FORM_ID}
      submitLabel={rule ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Grade"
          placeholder="A+"
          fullWidth
          required
          {...fieldProps(form, 'grade')}
        />
        <TextField
          label="Description"
          placeholder="Outstanding"
          fullWidth
          {...fieldProps(form, 'description')}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="From (%)"
            fullWidth
            required
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            {...fieldProps(form, 'minPercent')}
            helperText={form.formState.errors.minPercent?.message ?? 'Inclusive, for example 80'}
          />
          <TextField
            label="To (%)"
            fullWidth
            required
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            {...fieldProps(form, 'maxPercent')}
            helperText={form.formState.errors.maxPercent?.message ?? 'Inclusive, for example 89.99'}
          />
        </Stack>
      </Stack>
    </ResponsiveDialog>
  );
}
