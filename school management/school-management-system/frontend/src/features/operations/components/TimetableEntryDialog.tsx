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
import { blankToNull } from '../../../schemas/people';
import { timetableSchema, type TimetableFormValues } from '../../../schemas/operations';
import type { Section } from '../../../types/academics';
import type { DayName, TimetableEntry } from '../../../types/operations';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { shortTime } from '../../../utils/dates';
import { useAssignments } from '../../academics/hooks';
import { TeacherAutocomplete } from '../../academics/components/TeacherAutocomplete';
import { useSaveTimetableEntry } from '../hooks';
import { DAYS, DAY_LABELS } from '../labels';

const FORM_ID = 'timetable-form';

interface TimetableEntryDialogProps {
  section: Section;
  /** Null when adding a slot. The section of an existing slot cannot change. */
  entry: TimetableEntry | null;
  /** Pre-selected weekday when adding. */
  defaultDay?: DayName;
  onClose: () => void;
}

export function TimetableEntryDialog({
  section,
  entry,
  defaultDay,
  onClose,
}: TimetableEntryDialogProps) {
  const save = useSaveTimetableEntry();
  const notify = useNotify();
  const assignments = useAssignments({ page: 0, size: 100, sectionId: section.id });
  const [error, setError] = useState<ApiError | null>(null);
  const editing = entry !== null;
  const form = useForm<TimetableFormValues>({
    resolver: zodResolver(timetableSchema),
    defaultValues: {
      subjectId: entry?.subjectId ?? '',
      teacher: entry ? { id: entry.teacherId, label: entry.teacherName ?? 'Teacher' } : null,
      room: entry?.room ?? '',
      dayOfWeek: entry?.dayOfWeek ?? defaultDay ?? 'MONDAY',
      periodNumber: String(entry?.periodNumber ?? 1),
      startTime: entry ? shortTime(entry.startTime) : '',
      endTime: entry ? shortTime(entry.endTime) : '',
      effectiveFrom: entry?.effectiveFrom ?? '',
      effectiveTo: entry?.effectiveTo ?? '',
      active: entry?.active ?? true,
    },
  });

  const subjectOptions = (assignments.data?.content ?? [])
    .filter((assignment) => assignment.active)
    .map((assignment) => ({
      value: assignment.subject.id,
      label: `${assignment.subject.code} - ${assignment.subject.name}`,
    }));
  const chosenSubject = form.watch('subjectId');
  const assigned = assignments.data?.content.find((a) => a.subject.id === chosenSubject)?.teacher;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: entry?.id,
        body: {
          sectionId: editing ? undefined : section.id,
          subjectId: values.subjectId,
          teacherId: values.teacher?.id ?? null,
          room: blankToNull(values.room),
          dayOfWeek: values.dayOfWeek,
          periodNumber: Number(values.periodNumber),
          startTime: values.startTime,
          endTime: values.endTime,
          effectiveFrom: blankToNull(values.effectiveFrom),
          effectiveTo: blankToNull(values.effectiveTo),
          active: values.active,
          version: entry?.version,
        },
      });
      notify.success(editing ? 'Slot updated' : 'Slot added');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'startTime',
        'endTime',
        'effectiveFrom',
        'effectiveTo',
        'room',
      ]);
      setError(onField ? null : apiError);
    }
  });

  const row = { direction: { xs: 'column', sm: 'row' }, spacing: 2.5 } as const;
  const shrink = { inputLabel: { shrink: true } };

  return (
    <ResponsiveDialog
      title={editing ? 'Edit timetable slot' : 'Add timetable slot'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Add slot'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Class"
          value={`${section.gradeLevel.name} - ${section.name} (${section.academicYear.name})`}
          disabled
          fullWidth
        />
        <FormSelect
          control={form.control}
          name="subjectId"
          label="Subject"
          options={subjectOptions}
          required
          helperText="Only subjects assigned to this class are listed."
        />
        <TeacherAutocomplete
          control={form.control}
          name="teacher"
          label="Teacher"
          helperText={
            assigned
              ? `Leave empty to use the assigned teacher, ${assigned.name}.`
              : 'Leave empty to use the teacher assigned to the subject.'
          }
        />
        <Stack {...row}>
          <FormSelect
            control={form.control}
            name="dayOfWeek"
            label="Day"
            options={DAYS.map((day) => ({ value: day, label: DAY_LABELS[day] }))}
            required
          />
          <TextField
            label="Period"
            fullWidth
            required
            slotProps={{ htmlInput: { inputMode: 'numeric' } }}
            {...fieldProps(form, 'periodNumber')}
          />
        </Stack>
        <Stack {...row}>
          <TextField
            label="Start time"
            type="time"
            fullWidth
            required
            slotProps={shrink}
            {...fieldProps(form, 'startTime')}
          />
          <TextField
            label="End time"
            type="time"
            fullWidth
            required
            slotProps={shrink}
            {...fieldProps(form, 'endTime')}
          />
        </Stack>
        <TextField label="Room" fullWidth {...fieldProps(form, 'room')} />
        <Stack {...row}>
          <TextField
            label="Effective from"
            type="date"
            fullWidth
            slotProps={shrink}
            {...fieldProps(form, 'effectiveFrom')}
            helperText={
              form.formState.errors.effectiveFrom?.message ??
              'Empty means the start of the academic year.'
            }
          />
          <TextField
            label="Effective to"
            type="date"
            fullWidth
            slotProps={shrink}
            {...fieldProps(form, 'effectiveTo')}
            helperText={
              form.formState.errors.effectiveTo?.message ??
              'Empty means the end of the academic year.'
            }
          />
        </Stack>
        <FormSwitch control={form.control} name="active" label="Active" />
      </Stack>
    </ResponsiveDialog>
  );
}
