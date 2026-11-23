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
import { blankToNull, studentSchema, type StudentFormValues } from '../../../schemas/people';
import type { Gender, Student, StudentStatus } from '../../../types/people';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useSaveStudent } from '../hooks';
import { GENDER_LABELS, STUDENT_STATUS_LABELS, toOptions } from '../labels';

const FORM_ID = 'student-form';

const FIELDS = [
  'admissionNumber',
  'password',
  'firstName',
  'lastName',
  'email',
  'phoneNumber',
  'dateOfBirth',
  'admissionDate',
  'bloodGroup',
  'addressLine1',
  'addressLine2',
  'city',
  'state',
  'postalCode',
  'emergencyContactName',
  'emergencyContactPhone',
] as const;

interface StudentFormDialogProps {
  /** Null when creating. The admission number of an existing student cannot be changed. */
  student: Student | null;
  onClose: () => void;
}

export function StudentFormDialog({ student, onClose }: StudentFormDialogProps) {
  const save = useSaveStudent();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const editing = student !== null;
  const schema = useMemo(() => studentSchema(!editing), [editing]);
  const form = useForm<StudentFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      admissionNumber: student?.admissionNumber ?? '',
      password: '',
      firstName: student?.firstName ?? '',
      lastName: student?.lastName ?? '',
      email: student?.email ?? '',
      phoneNumber: student?.phoneNumber ?? '',
      dateOfBirth: student?.dateOfBirth ?? '',
      gender: student?.gender ?? 'MALE',
      admissionDate: student?.admissionDate ?? '',
      bloodGroup: student?.bloodGroup ?? '',
      addressLine1: student?.addressLine1 ?? '',
      addressLine2: student?.addressLine2 ?? '',
      city: student?.city ?? '',
      state: student?.state ?? '',
      postalCode: student?.postalCode ?? '',
      emergencyContactName: student?.emergencyContactName ?? '',
      emergencyContactPhone: student?.emergencyContactPhone ?? '',
      status: student?.status ?? 'ACTIVE',
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await save.mutateAsync({
        id: student?.id,
        body: {
          admissionNumber: editing ? undefined : values.admissionNumber,
          password: editing ? undefined : values.password,
          firstName: values.firstName,
          lastName: values.lastName,
          email: values.email,
          phoneNumber: blankToNull(values.phoneNumber),
          dateOfBirth: values.dateOfBirth,
          gender: values.gender as Gender,
          admissionDate: values.admissionDate,
          bloodGroup: blankToNull(values.bloodGroup),
          addressLine1: values.addressLine1,
          addressLine2: blankToNull(values.addressLine2),
          city: values.city,
          state: blankToNull(values.state),
          postalCode: blankToNull(values.postalCode),
          emergencyContactName: values.emergencyContactName,
          emergencyContactPhone: values.emergencyContactPhone,
          status: editing ? (values.status as StudentStatus) : undefined,
          version: student?.version,
        },
      });
      notify.success(editing ? 'Student updated' : 'Student created');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, FIELDS);
      setError(onField ? null : apiError);
    }
  });

  const row = { direction: { xs: 'column', sm: 'row' }, spacing: 2.5 } as const;
  const dateLabel = { inputLabel: { shrink: true } };

  return (
    <ResponsiveDialog
      title={editing ? 'Edit student' : 'New student'}
      formId={FORM_ID}
      submitLabel={editing ? 'Save changes' : 'Create'}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        {editing ? (
          <TextField label="Admission number" value={student.admissionNumber} disabled fullWidth />
        ) : (
          <>
            <TextField
              label="Admission number"
              fullWidth
              required
              {...fieldProps(form, 'admissionNumber')}
              helperText={
                form.formState.errors.admissionNumber?.message ??
                "Also the student's sign-in ID. It cannot be changed later."
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
            label="Date of birth"
            type="date"
            fullWidth
            required
            slotProps={dateLabel}
            {...fieldProps(form, 'dateOfBirth')}
          />
          <FormSelect
            control={form.control}
            name="gender"
            label="Gender"
            options={toOptions<Gender>(GENDER_LABELS)}
            required
          />
        </Stack>
        <Stack {...row}>
          <TextField
            label="Admission date"
            type="date"
            fullWidth
            required
            slotProps={dateLabel}
            {...fieldProps(form, 'admissionDate')}
          />
          <TextField
            label="Blood group"
            placeholder="O+"
            fullWidth
            {...fieldProps(form, 'bloodGroup')}
          />
        </Stack>
        {editing ? (
          <FormSelect
            control={form.control}
            name="status"
            label="Status"
            options={toOptions<StudentStatus>(STUDENT_STATUS_LABELS)}
            helperText="Withdrawn or graduated students are removed from their current section."
          />
        ) : null}
        <TextField label="Address" fullWidth required {...fieldProps(form, 'addressLine1')} />
        <TextField label="Address line 2" fullWidth {...fieldProps(form, 'addressLine2')} />
        <Stack {...row}>
          <TextField label="City" fullWidth required {...fieldProps(form, 'city')} />
          <TextField label="State" fullWidth {...fieldProps(form, 'state')} />
          <TextField label="Postal code" fullWidth {...fieldProps(form, 'postalCode')} />
        </Stack>
        <Stack {...row}>
          <TextField
            label="Emergency contact name"
            fullWidth
            required
            {...fieldProps(form, 'emergencyContactName')}
          />
          <TextField
            label="Emergency contact phone"
            type="tel"
            fullWidth
            required
            {...fieldProps(form, 'emergencyContactPhone')}
          />
        </Stack>
      </Stack>
    </ResponsiveDialog>
  );
}
