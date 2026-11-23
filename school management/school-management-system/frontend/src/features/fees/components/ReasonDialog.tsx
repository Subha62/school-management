import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { MAX_REASON, MIN_REASON, reasonSchema, type ReasonFormValues } from '../../../schemas/fees';
import { applyServerFieldErrors, fieldPropsWithHint } from '../../../utils/forms';

const FORM_ID = 'fee-reason-form';

interface ReasonDialogProps {
  title: string;
  /** What is about to happen, shown above the reason field. */
  description: string;
  submitLabel: string;
  successMessage: string;
  /** Performs the action; a rejection is shown inside the dialog. */
  onSubmit: (reason: string) => Promise<unknown>;
  onClose: () => void;
}

/** A dialog that asks for a written reason before waiving a fee, removing a waiver or reversing a payment. */
export function ReasonDialog({
  title,
  description,
  submitLabel,
  successMessage,
  onSubmit: perform,
  onClose,
}: ReasonDialogProps) {
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const form = useForm<ReasonFormValues>({
    resolver: zodResolver(reasonSchema),
    defaultValues: { reason: '' },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await perform(values.reason);
      notify.success(successMessage);
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      setError(applyServerFieldErrors(form, apiError, ['reason']) ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title={title}
      formId={FORM_ID}
      submitLabel={submitLabel}
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        <Typography sx={{ overflowWrap: 'anywhere' }}>{description}</Typography>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Reason"
          fullWidth
          required
          multiline
          minRows={2}
          {...fieldPropsWithHint(form, 'reason', `${MIN_REASON} to ${MAX_REASON} characters.`)}
        />
      </Stack>
    </ResponsiveDialog>
  );
}
