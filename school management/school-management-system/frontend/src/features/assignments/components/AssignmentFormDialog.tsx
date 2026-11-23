import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect } from '../../../components/FormSelect';
import { FormSwitch } from '../../../components/FormSwitch';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { assignmentSchema, type AssignmentFormValues } from '../../../schemas/assignments';
import { blankToNull } from '../../../schemas/people';
import type { Assignment } from '../../../types/assignments';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useMyClasses } from '../../operations/hooks';
import { useSaveAssignment } from '../hooks';
import { fromLocalInput, toLocalInput } from '../labels';

const FORM_ID = 'assignment-form';

interface AssignmentFormDialogProps {
  /** Null when creating. The class and subject of an existing assignment cannot change. */
  assignment: Assignment | null;
  onClose: () => void;
  onSaved?: (assignment: Assignment) => void;
}

export function AssignmentFormDialog({ assignment, onClose, onSaved }: AssignmentFormDialogProps) {
  const save = useSaveAssignment();
  const notify = useNotify();
  const classes = useMyClasses();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = assignment !== null;
  const originalDue = assignment ? toLocalInput(assignment.dueAt) : null;
  const schema = useMemo(
    () => assignmentSchema(!editing, originalDue, () => Date.now()),
    [editing, originalDue],
  );
  const form = useForm<AssignmentFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      target: '',
      title: assignment?.title ?? '',
      description: assignment?.description ?? '',
      dueAt: originalDue ?? '',
      maxMarks: String(assignment?.maxMarks ?? ''),
      allowLate: assignment?.allowLate ?? false,
    },
  });

  // Only subjects the teacher teaches in a class can get assignments.
  const targets = (classes.data ?? []).flatMap((option) =>
    option.subjects.map((subject) => ({
      value: `${option.sectionId}|${subject.id}`,
      label: `${option.sectionName} · ${subject.name}`,
    })),
  );

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    const [sectionId, subjectId] = values.target.split('|');
    try {
      const saved = await save.mutateAsync({
        id: assignment?.id,
        body: {
          sectionId: editing ? undefined : sectionId,
          subjectId: editing ? undefined : subjectId,
          title: values.title,
          description: blankToNull(values.description),
          dueAt: fromLocalInput(values.dueAt),
          maxMarks: Number(values.maxMarks),
          allowLate: values.allowLate,
          version: assignment?.version,
        },
      });
      notify.success(editing ? 'Assignment updated' : 'Draft created');
      onSaved?.(saved);
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'title',
        'description',
        'dueAt',
        'maxMarks',
      ]);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={editing ? 'Edit assignment' : 'New assignment'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create draft'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        {editing ? (
          <TextField
            label="Class and subject"
            value={`${assignment.sectionName} · ${assignment.subjectName}`}
            disabled
            fullWidth
          />
        ) : (
          <FormSelect
            control={form.control}
            name="target"
            label="Class and subject"
            options={targets}
            required
            helperText={
              classes.data && targets.length === 0
                ? 'You have no subject assigned in the active academic year.'
                : undefined
            }
          />
        )}
        <TextField label="Title" fullWidth required {...fieldProps(form, 'title')} />
        <TextField
          label="Instructions"
          fullWidth
          multiline
          minRows={3}
          {...fieldProps(form, 'description')}
        />
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5}>
          <TextField
            label="Due date and time"
            type="datetime-local"
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'dueAt')}
          />
          <TextField
            label="Maximum marks"
            fullWidth
            required
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
            {...fieldProps(form, 'maxMarks')}
          />
        </Stack>
        <FormSwitch control={form.control} name="allowLate" label="Accept late submissions" />
      </Stack>
    </ResponsiveDialog>
  );
}
