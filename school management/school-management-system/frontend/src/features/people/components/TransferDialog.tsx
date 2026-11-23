import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect } from '../../../components/FormSelect';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { rollNumberOrNull, transferSchema, type TransferFormValues } from '../../../schemas/people';
import type { Enrollment } from '../../../types/people';
import { fieldProps } from '../../../utils/forms';
import { useSectionOptions } from '../../academics/hooks';
import { useTransferEnrollment } from '../hooks';

const FORM_ID = 'transfer-form';

interface TransferDialogProps {
  enrollment: Enrollment;
  onClose: () => void;
}

/** Moves a student to another section of the same academic year; the old enrollment stays in the history. */
export function TransferDialog({ enrollment, onClose }: TransferDialogProps) {
  const transfer = useTransferEnrollment();
  const notify = useNotify();
  const sections = useSectionOptions(enrollment.academicYear.id);
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<TransferFormValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: { targetSectionId: '', rollNumber: '' },
  });

  const options = (sections.data?.content ?? [])
    .filter((section) => section.active && section.id !== enrollment.section.id)
    .map((section) => ({
      value: section.id,
      label: `${section.gradeLevel.name} - ${section.name} (capacity ${section.capacity})`,
    }));

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await transfer.mutateAsync({
        id: enrollment.id,
        body: {
          targetSectionId: values.targetSectionId,
          rollNumber: rollNumberOrNull(values.rollNumber),
        },
      });
      notify.success('Student transferred');
      onClose();
    } catch (cause) {
      setError(toApiError(cause));
    }
  });

  return (
    <ResponsiveDialog
      title="Transfer student"
      formId={FORM_ID}
      submitLabel="Transfer"
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <Alert severity="info">
          {enrollment.student.name} is in {enrollment.gradeLevel.name} - {enrollment.section.name}{' '}
          for {enrollment.academicYear.name}. A transfer stays within this academic year.
        </Alert>
        <FormSelect
          control={form.control}
          name="targetSectionId"
          label="New section"
          options={options}
          required
        />
        <TextField
          label="Roll number"
          fullWidth
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          {...fieldProps(form, 'rollNumber')}
          helperText={
            form.formState.errors.rollNumber?.message ?? 'Leave empty to use the next free number.'
          }
        />
      </Stack>
    </ResponsiveDialog>
  );
}
