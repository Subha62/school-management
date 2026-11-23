import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect } from '../../../components/FormSelect';
import { InitialPasswordField } from '../../../components/InitialPasswordField';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { blankToNull, teacherSchema, type TeacherFormValues } from '../../../schemas/people';
import type { EmploymentStatus, Teacher } from '../../../types/people';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveTeacher } from '../hooks';
import { EMPLOYMENT_LABELS, toOptions } from '../labels';

const FORM_ID = 'teacher-form';

const FIELDS = [
  'employeeId',
  'password',
  'firstName',
  'lastName',
  'email',
  'phoneNumber',
  'joiningDate',
  'qualification',
  'specialization',
] as const;

interface TeacherFormDialogProps {
  /** Null when creating. The employee ID of an existing teacher cannot be changed. */
  teacher: Teacher | null;
  onClose: () => void;
}

export function TeacherFormDialog({ teacher, onClose }: TeacherFormDialogProps) {
  const save = useSaveTeacher();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = teacher !== null;
  const schema = useMemo(() => teacherSchema(!editing), [editing]);
  const form = useForm<TeacherFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      employeeId: teacher?.employeeId ?? '',
      password: '',
      firstName: teacher?.firstName ?? '',
      lastName: teacher?.lastName ?? '',
      email: teacher?.email ?? '',
      phoneNumber: teacher?.phoneNumber ?? '',
      joiningDate: teacher?.joiningDate ?? '',
      qualification: teacher?.qualification ?? '',
      specialization: teacher?.specialization ?? '',
      employmentStatus: teacher?.employmentStatus ?? 'ACTIVE',
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: teacher?.id,
        body: {
          employeeId: editing ? undefined : values.employeeId,
          password: editing ? undefined : values.password,
          firstName: values.firstName,
          lastName: values.lastName,
          email: values.email,
          phoneNumber: blankToNull(values.phoneNumber),
          joiningDate: values.joiningDate,
          qualification: blankToNull(values.qualification),
          specialization: blankToNull(values.specialization),
          employmentStatus: values.employmentStatus as EmploymentStatus,
          version: teacher?.version,
        },
      });
      notify.success(editing ? 'Teacher updated' : 'Teacher created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, FIELDS);
      setError(onField ? null : apiError);
    }
  });

  const row = { direction: { xs: 'column', sm: 'row' }, spacing: 2.5 } as const;

  return (
    <ResponsiveDialog
      title={editing ? 'Edit teacher' : 'New teacher'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        {editing ? (
          <TextField label="Employee ID" value={teacher.employeeId} disabled fullWidth />
        ) : (
          <>
            <TextField
              label="Employee ID"
              fullWidth
              required
              {...fieldProps(form, 'employeeId')}
              helperText={
                form.formState.errors.employeeId?.message ??
                'Also the teacher\u2019s sign-in ID. It cannot be changed later.'
              }
            />
            <InitialPasswordField
              {...fieldProps(form, 'password')}
              onGenerate={(value) =>
                form.setValue('password', value, { shouldDirty: true, shouldValidate: true })
              }
            />
          </>
        )}
        <Stack {...row}>
          <TextField label="First name" fullWidth required {...fieldProps(form, 'firstName')} />
          <TextField label="Last name" fullWidth required {...fieldProps(form, 'lastName')} />
        </Stack>
        <Stack {...row}>
          <TextField label="Email" type="email" fullWidth required {...fieldProps(form, 'email')} />
          <TextField label="Phone" type="tel" fullWidth {...fieldProps(form, 'phoneNumber')} />
        </Stack>
        <Stack {...row}>
          <TextField
            label="Joining date"
            type="date"
            fullWidth
            required
            slotProps={{ inputLabel: { shrink: true } }}
            {...fieldProps(form, 'joiningDate')}
          />
          <FormSelect
            control={form.control}
            name="employmentStatus"
            label="Employment status"
            options={toOptions<EmploymentStatus>(EMPLOYMENT_LABELS)}
            required
            helperText="Only active teachers can be assigned to sections and subjects."
          />
        </Stack>
        <TextField label="Qualification" fullWidth {...fieldProps(form, 'qualification')} />
        <TextField label="Specialization" fullWidth {...fieldProps(form, 'specialization')} />
      </Stack>
    </ResponsiveDialog>
  );
}
