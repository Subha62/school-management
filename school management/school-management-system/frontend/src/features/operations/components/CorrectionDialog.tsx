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
import { blankToNull } from '../../../schemas/people';
import { correctionSchema, type CorrectionFormValues } from '../../../schemas/operations';
import type { AttendanceEntry, AttendanceStatus } from '../../../types/operations';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useCorrectAttendance } from '../hooks';
import { STATUS_LABELS, STATUS_ORDER } from '../labels';

const FORM_ID = 'correction-form';

interface CorrectionDialogProps {
  sessionId: string;
  entry: AttendanceEntry;
  onClose: () => void;
}

/** An administrator's change to one student's mark. The reason is mandatory and kept in the history. */
export function CorrectionDialog({ sessionId, entry, onClose }: CorrectionDialogProps) {
  const correct = useCorrectAttendance();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<CorrectionFormValues>({
    resolver: zodResolver(correctionSchema),
    defaultValues: { status: entry.status ?? 'PRESENT', remarks: entry.remarks ?? '', reason: '' },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await correct.mutateAsync({
        id: sessionId,
        body: {
          studentId: entry.studentId,
          status: values.status as AttendanceStatus,
          remarks: blankToNull(values.remarks),
          reason: values.reason,
        },
      });
      notify.success('Attendance corrected');
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, ['reason', 'remarks']);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={`Correct attendance: ${entry.name}`}
      formId={FORM_ID}
      submitLabel="Save correction"
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <Alert severity="info">
          Currently {entry.status ? STATUS_LABELS[entry.status] : 'not marked'}. Every correction is
          recorded with your name and the reason.
        </Alert>
        <FormSelect
          control={form.control}
          name="status"
          label="New status"
          options={STATUS_ORDER.map((status) => ({ value: status, label: STATUS_LABELS[status] }))}
          required
        />
        <TextField label="Remarks" fullWidth {...fieldProps(form, 'remarks')} />
        <TextField
          label="Reason for the correction"
          fullWidth
          required
          multiline
          minRows={2}
          {...fieldProps(form, 'reason')}
        />
      </Stack>
    </ResponsiveDialog>
  );
}
