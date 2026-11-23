import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { academicYearSchema, type AcademicYearFormValues } from '../../../schemas/academics';
import type { AcademicYear } from '../../../types/academics';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveAcademicYear } from '../hooks';

const FORM_ID = 'academic-year-form';

/** Mount it to open it; leaving it unmounted closes it and discards any edits. */
export function AcademicYearFormDialog({
  year,
  onClose,
}: {
  year: AcademicYear | null;
  onClose: () => void;
}) {
  const save = useSaveAcademicYear();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<AcademicYearFormValues>({
    resolver: zodResolver(academicYearSchema),
    defaultValues: {
      name: year?.name ?? '',
      startDate: year?.startDate ?? '',
      endDate: year?.endDate ?? '',
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({ id: year?.id, body: { ...values, version: year?.version } });
      notify.success(year ? 'Academic year updated' : 'Academic year created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['name', 'startDate', 'endDate']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={year ? 'Edit academic year' : 'New academic year'}
      formId={FORM_ID}
      submitLabel={year ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Name"
          placeholder="2026-2027"
          fullWidth
          required
          {...fieldProps(form, 'name')}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Start date"
            type="date"
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'startDate')}
          />
          <TextField
            label="End date"
            type="date"
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'endDate')}
          />
        </Stack>
      </Stack>
    </ResponsiveDialog>
  );
}
