import type { FeeStatus, PaymentMethod } from '../../types/fees';

/** The school's currency; every amount in the app is shown in it. */
export const CURRENCY = 'INR';

const moneyFormat = new Intl.NumberFormat(undefined, { style: 'currency', currency: CURRENCY });

/** An amount as returned by the server, for example "₹1,500.00". */
export const formatMoney = (amount: number): string => moneyFormat.format(amount);

export const FEE_STATUS_LABELS: Record<FeeStatus, string> = {
  NOT_DUE: 'Not due',
  DUE: 'Due',
  PARTIALLY_PAID: 'Partially paid',
  PAID: 'Paid',
  OVERDUE: 'Overdue',
  WAIVED: 'Waived',
};

export const FEE_STATUS_ORDER: FeeStatus[] = [
  'NOT_DUE',
  'DUE',
  'PARTIALLY_PAID',
  'OVERDUE',
  'PAID',
  'WAIVED',
];

export const feeTone = (status: FeeStatus) =>
  status === 'PAID'
    ? 'success'
    : status === 'OVERDUE'
      ? 'warning'
      : status === 'PARTIALLY_PAID' || status === 'DUE'
        ? 'info'
        : 'default';

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Cash',
  CHEQUE: 'Cheque',
  BANK_TRANSFER: 'Bank transfer',
  UPI: 'UPI',
  CARD: 'Card',
  OTHER: 'Other',
};

export const PAYMENT_METHOD_ORDER: PaymentMethod[] = [
  'CASH',
  'CHEQUE',
  'BANK_TRANSFER',
  'UPI',
  'CARD',
  'OTHER',
];

/** A fee can take a payment unless it is waived or already fully paid. */
export const canRecordPayment = (status: FeeStatus): boolean =>
  status !== 'WAIVED' && status !== 'PAID';
