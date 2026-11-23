import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { StatusChip } from '../../../components/StatusChip';
import type { Payment, StudentFeeDetail } from '../../../types/fees';
import { formatDate } from '../../../utils/format';
import { formatInstant } from '../../exams/labels';
import { PAYMENT_METHOD_LABELS, formatMoney } from '../labels';
import { FeeStatusChip } from './FeeStatusChip';

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontWeight: 600, overflowWrap: 'anywhere' }}>
        {children}
      </Typography>
    </Box>
  );
}

interface FeeDetailViewProps {
  detail: StudentFeeDetail;
  /** Where the receipt of a payment is shown. */
  receiptHref: (payment: Payment) => string;
  /** Show who the fee belongs to; students do not need it. */
  showStudent?: boolean;
}

/** One fee with its payments, including reversed ones. Shared by administrators and students. */
export function FeeDetailView({ detail, receiptHref, showStudent = false }: FeeDetailViewProps) {
  const { fee, payments } = detail;
  const overdue = fee.status === 'OVERDUE';

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', justifyContent: 'space-between', mb: 2 }}
        >
          <Typography variant="h6" component="h2" sx={{ overflowWrap: 'anywhere' }}>
            {fee.categoryName}
            {fee.description ? ` · ${fee.description}` : ''}
          </Typography>
          <FeeStatusChip status={fee.status} />
        </Stack>
        <Box
          component="dl"
          sx={{
            m: 0,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' },
          }}
        >
          {showStudent ? (
            <>
              <Fact label="Student">{fee.studentName}</Fact>
              <Fact label="Admission no.">{fee.admissionNumber}</Fact>
              <Fact label="Class">{fee.className ?? '-'}</Fact>
            </>
          ) : null}
          <Fact label="Academic year">{fee.academicYearName}</Fact>
          <Fact label="Due date">{formatDate(fee.dueDate)}</Fact>
          <Fact label="Amount">{formatMoney(fee.amount)}</Fact>
          <Fact label="Paid">{formatMoney(fee.paidAmount)}</Fact>
          <Box sx={{ minWidth: 0 }}>
            <Typography component="dt" variant="body2" color="text.secondary">
              Balance
            </Typography>
            <Typography
              component="dd"
              sx={{
                m: 0,
                fontWeight: 700,
                color: overdue ? 'warning.dark' : 'text.primary',
              }}
            >
              {formatMoney(fee.balance)}
            </Typography>
          </Box>
        </Box>
        {fee.status === 'WAIVED' ? (
          <Typography sx={{ mt: 2, overflowWrap: 'anywhere' }}>
            Waived{fee.waivedAt ? ` on ${formatInstant(fee.waivedAt)}` : ''}
            {fee.waiveReason ? `: ${fee.waiveReason}` : ''}
          </Typography>
        ) : null}
      </Paper>

      <Box component="section" aria-labelledby="payments-heading">
        <Typography variant="h6" component="h2" id="payments-heading" sx={{ mb: 1.5 }}>
          Payments
        </Typography>
        {payments.length === 0 ? (
          <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
            <Typography color="text.secondary">No payments have been recorded yet.</Typography>
          </Paper>
        ) : (
          <Stack component="ul" spacing={1.5} sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {payments.map((payment) => (
              <Paper key={payment.id} component="li" variant="outlined" sx={{ p: 2 }}>
                <Stack
                  direction="row"
                  spacing={1}
                  useFlexGap
                  sx={{ flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between' }}
                >
                  <Link
                    component={RouterLink}
                    to={receiptHref(payment)}
                    underline="hover"
                    aria-label={`Receipt ${payment.receiptNumber}`}
                    sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}
                  >
                    {payment.receiptNumber}
                  </Link>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                    {payment.reversed ? <StatusChip label="Reversed" tone="warning" /> : null}
                    <Typography
                      sx={{
                        fontWeight: 700,
                        textDecoration: payment.reversed ? 'line-through' : 'none',
                      }}
                    >
                      {formatMoney(payment.amount)}
                    </Typography>
                  </Stack>
                </Stack>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ overflowWrap: 'anywhere' }}
                >
                  {formatDate(payment.paidOn)} · {PAYMENT_METHOD_LABELS[payment.method]}
                  {payment.reference ? ` · ${payment.reference}` : ''}
                  {payment.receivedByName ? ` · received by ${payment.receivedByName}` : ''}
                </Typography>
                {payment.reversed ? (
                  <Typography variant="body2" sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>
                    Reversed{payment.reversedAt ? ` on ${formatInstant(payment.reversedAt)}` : ''}
                    {payment.reversedByName ? ` by ${payment.reversedByName}` : ''}
                    {payment.reversalReason ? `: ${payment.reversalReason}` : ''}
                  </Typography>
                ) : null}
              </Paper>
            ))}
          </Stack>
        )}
      </Box>
    </Stack>
  );
}
