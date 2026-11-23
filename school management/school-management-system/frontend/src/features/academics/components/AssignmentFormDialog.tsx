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
import { assignmentSchema, type AssignmentFormValues } from '../../../schemas/academics';
import type { SectionSubject } from '../../../types/academics';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveAssignment, useSubjectOptions } from '../hooks';
import { TeacherAutocomplete } from './TeacherAutocomplete';

const FORM_ID = 'assignment-form';

interface AssignmentFormDialogProps {
  /** The section being edited; always known because assignments are managed per section. */
  sectionId: string;
  sectionLabel: string;
  /** Null when adding a subject. */
  assignment: SectionSubject | null;
  onClose: () => void;
}

export function AssignmentFormDialog({
  sectionId,
  sectionLabel,
  assignment,
  onClose,
}: AssignmentFormDialogProps) {
  const save = useSaveAssignment();
  const notify = useNotify();
  const subjects = useSubjectOptions(true);
  const [error, setError] = useState<ApiError | null>(null);
  const editing = assignment !== null;
  const form = useForm<AssignmentFormValues>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: {
      subjectId: assignment?.subject.id ?? '',
      teacher: assignment?.teacher
        ? { id: assignment.teacher.id, label: assignment.teacher.name }
        : null,
      weeklyPeriods: String(assignment?.weeklyPeriods ?? 5),
      active: assignment?.active ?? true,
    },
  });

  const subjectOptions = (subjects.data?.content ?? []).map((subject) => ({
    value: subject.id,
    label: `${subject.code} - ${subject.name}`,
  }));
  if (assignment && !subjectOptions.some((option) => option.value === assignment.subject.id)) {
    subjectOptions.unshift({
      value: assignment.subject.id,
      label: `${assignment.subject.code} - ${assignment.subject.name}`,
    });
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: assignment?.id,
        body: {
          sectionId: assignment ? undefined : sectionId,
          subjectId: assignment ? undefined : values.subjectId,
          teacherId: values.teacher?.id ?? null,
          weeklyPeriods: Number(values.weeklyPeriods),
          active: values.active,
          version: assignment?.version,
        },
      });
      notify.success(editing ? 'Assignment updated' : 'Subject assigned');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['weeklyPeriods']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit assignment' : 'Assign a subject'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Assign'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField label="Section" value={sectionLabel} fullWidth disabled />
        <FormSelect
          control={form.control}
          name="subjectId"
          label="Subject"
          options={subjectOptions}
          required
          disabled={editing}
        />
        <TeacherAutocomplete
          control={form.control}
          name="teacher"
          label="Teacher"
          helperText="Optional. Leave empty to assign the teacher later."
        />
        <TextField
          label="Weekly periods"
          fullWidth
          required
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
          {...fieldProps(form, 'weeklyPeriods')}
        />
        {editing ? <FormSwitch control={form.control} name="active" label="Active" /> : null}
      </Stack>
    </ResponsiveDialog>
  );
}
