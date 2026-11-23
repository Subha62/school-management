import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import { FormSelect } from '../../../components/FormSelect';
import { ResponsiveDialog } from '../../../components/ResponsiveDialog';
import { blankToNull } from '../../../schemas/people';
import { paymentSchema, type PaymentFormValues } from '../../../schemas/fees';
import type { StudentFee } from '../../../types/fees';
import { todayIso } from '../../../utils/dates';
import { applyServerFieldErrors, fieldProps } from '../../../utils/forms';
import { useRecordPayment } from '../hooks';
import { PAYMENT_METHOD_LABELS, PAYMENT_METHOD_ORDER, formatMoney } from '../labels';

const FORM_ID = 'record-payment-form';

const METHOD_OPTIONS = PAYMENT_METHOD_ORDER.map((value) => ({
  value,
  label: PAYMENT_METHOD_LABELS[value],
}));

export function RecordPaymentDialog({ fee, onClose }: { fee: StudentFee; onClose: () => void }) {
  const record = useRecordPayment();
  const notify = useNotify();
  const [error, setError] = useState<ApiError | null>(null);
  const today = todayIso();
  const schema = useMemo(() => paymentSchema(today), [today]);
  const form = useForm<PaymentFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      amount: fee.balance.toFixed(2),
      method: 'CASH',
      reference: '',
      paidOn: today,
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const payment = await record.mutateAsync({
        feeId: fee.id,
        body: {
          amount: Number(values.amount),
          method: values.method,
          reference: blankToNull(values.reference),
          paidOn: values.paidOn,
        },
      });
      notify.success(`Payment recorded. Receipt ${payment.receiptNumber}`);
      onClose();
    } catch (cause) {
      const apiError = toApiError(cause);
      const onField = applyServerFieldErrors(form, apiError, [
        'amount',
        'method',
        'reference',
        'paidOn',
      ]);
      setError(onField ? null : apiError);
    }
  });

  return (
    <ResponsiveDialog
      title="Record payment"
      formId={FORM_ID}
      submitLabel="Record payment"
      submitting={form.formState.isSubmitting}
      onClose={onClose}
    >
      <Stack component="form" id={FORM_ID} onSubmit={onSubmit} noValidate spacing={2.5}>
        <Typography sx={{ overflowWrap: 'anywhere' }}>
          {fee.studentName} · {fee.categoryName}
          {fee.description ? ` (${fee.description})` : ''}
          <br />
          Balance: <strong>{formatMoney(fee.balance)}</strong> of {formatMoney(fee.amount)}
        </Typography>
        {error ? <Alert severity="error">{error.message}</Alert> : null}
        <TextField
          label="Amount"
          fullWidth
          required
          slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          {...fieldProps(form, 'amount')}
        />
        <FormSelect
          control={form.control}
          name="method"
          label="Payment method"
          options={METHOD_OPTIONS}
          required
        />
        <TextField
          label="Reference"
          fullWidth
          placeholder="Cheque or transaction number"
          {...fieldProps(form, 'reference')}
        />
        <TextField
          label="Payment date"
          type="date"
          fullWidth
          required
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today } }}
          {...fieldProps(form, 'paidOn')}
        />
      </Stack>
    </ResponsiveDialog>
  );
}
