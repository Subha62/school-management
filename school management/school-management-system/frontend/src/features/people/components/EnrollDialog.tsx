import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ChoiceAutocomplete, type Choice } from '../../../components/ChoiceAutocomplete';
import { FormSelect } from '../../../components/FormSelect';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { useSectionOptions, useYearOptions } from '../../academics/hooks';
import { enrollSchema, rollNumberOrNull, type EnrollFormValues } from '../../../schemas/people';
import { fieldProps } from '../../../utils/forms';
import { useEnrollStudent, useStudentChoices } from '../hooks';

const FORM_ID = 'enroll-form';

interface EnrollDialogProps {
  /** Pre-selected student, for enrolling from a student's own page. */
  student?: Choice;
  /** Pre-selected academic year, usually the one the list is filtered by. */
  defaultYearId?: string;
  onClose: () => void;
}

export function EnrollDialog({ student, defaultYearId, onClose }: EnrollDialogProps) {
  const enroll = useEnrollStudent();
  const notify = useNotify();
  const years = useYearOptions();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<EnrollFormValues>({
    resolver: zodResolver(enrollSchema),
    defaultValues: {
      student: student ?? null,
      academicYearId: defaultYearId ?? '',
      sectionId: '',
      rollNumber: '',
    },
  });
  const yearId = form.watch('academicYearId');
  const sections = useSectionOptions(yearId || undefined);

  // Closed years cannot receive students, and neither can inactive sections.
  const yearOptions = (years.data?.content ?? [])
    .filter((year) => year.status !== 'CLOSED')
    .map((year) => ({ value: year.id, label: year.name }));
  const sectionOptions = (sections.data?.content ?? [])
    .filter((section) => section.active)
    .map((section) => ({
      value: section.id,
      label: `${section.gradeLevel.name} - ${section.name} (capacity ${section.capacity})`,
    }));

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await enroll.mutateAsync({
        studentId: values.student?.id ?? '',
        sectionId: values.sectionId,
        rollNumber: rollNumberOrNull(values.rollNumber),
      });
      notify.success('Student enrolled');
      onClose();
    } catch (cause) {
      setError(toApiError(cause));
    }
  });

  return (
    <ResponsiveDialog
      title="Enroll student"
      formId={FORM_ID}
      submitLabel="Enroll"
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <ChoiceAutocomplete
          control={form.control}
          name="student"
          label="Student"
          required
          disabled={Boolean(student)}
          noOptionsText="No matching active students"
          useOptions={useStudentChoices}
        />
        <FormSelect
          control={form.control}
          name="academicYearId"
          label="Academic year"
          options={yearOptions}
          required
        />
        <FormSelect
          control={form.control}
          name="sectionId"
          label="Section"
          options={sectionOptions}
          required
          disabled={!yearId}
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
