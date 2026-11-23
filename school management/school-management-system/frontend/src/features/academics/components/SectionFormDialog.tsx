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
import { sectionSchema, type SectionFormValues } from '../../../schemas/academics';
import type { Section } from '../../../types/academics';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useGradeOptions, useSaveSection, useYearOptions } from '../hooks';
import { TeacherAutocomplete } from './TeacherAutocomplete';

const FORM_ID = 'section-form';

interface SectionFormDialogProps {
  /** Null when creating. The year and grade of an existing section cannot be changed. */
  section: Section | null;
  /** Pre-selected year when creating, usually the one the list is filtered by. */
  defaultYearId?: string;
  onClose: () => void;
}

export function SectionFormDialog({ section, defaultYearId, onClose }: SectionFormDialogProps) {
  const save = useSaveSection();
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions(true);
  const [error, setError] = useState<ApiError | null>(null);
  const editing = section !== null;
  const form = useForm<SectionFormValues>({
    resolver: zodResolver(sectionSchema),
    defaultValues: {
      academicYearId: section?.academicYear.id ?? defaultYearId ?? '',
      gradeLevelId: section?.gradeLevel.id ?? '',
      name: section?.name ?? '',
      capacity: String(section?.capacity ?? 40),
      classTeacher: section?.classTeacher
        ? { id: section.classTeacher.id, label: section.classTeacher.name }
        : null,
      active: section?.active ?? true,
    },
  });

  // Closed years cannot receive sections; the grade list contains active grades only.
  const yearOptions = (years.data?.content ?? [])
    .filter((year) => year.status !== 'CLOSED' || year.id === section?.academicYear.id)
    .map((year) => ({ value: year.id, label: year.name }));
  const gradeOptions = (grades.data?.content ?? []).map((grade) => ({
    value: grade.id,
    label: grade.name,
  }));
  if (section && !gradeOptions.some((option) => option.value === section.gradeLevel.id)) {
    gradeOptions.unshift({ value: section.gradeLevel.id, label: section.gradeLevel.name });
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: section?.id,
        body: {
          academicYearId: values.academicYearId,
          gradeLevelId: values.gradeLevelId,
          name: values.name,
          capacity: Number(values.capacity),
          classTeacherId: values.classTeacher?.id ?? null,
          active: values.active,
          version: section?.version,
        },
      });
      notify.success(editing ? 'Section updated' : 'Section created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['name', 'capacity']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit section' : 'New section'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <FormSelect
          control={form.control}
          name="academicYearId"
          label="Academic year"
          options={yearOptions}
          required
          disabled={editing}
        />
        <FormSelect
          control={form.control}
          name="gradeLevelId"
          label="Grade"
          options={gradeOptions}
          required
          disabled={editing}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Section name"
            placeholder="A"
            fullWidth
            required
            {...fieldProps(form, 'name')}
          />
          <TextField
            label="Capacity"
            fullWidth
            required
            slotProps={{ htmlInput: { inputMode: 'numeric' } }}
            {...fieldProps(form, 'capacity')}
          />
        </Stack>
        <TeacherAutocomplete control={form.control} name="classTeacher" label="Class teacher" />
        <FormSwitch control={form.control} name="active" label="Active" />
      </Stack>
    </ResponsiveDialog>
  );
}
