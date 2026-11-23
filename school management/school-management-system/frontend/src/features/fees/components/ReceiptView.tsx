import PrintIcon from '@mui/icons-material/Print';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import GlobalStyles from '@mui/material/GlobalStyles';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import type { Receipt } from '../../../types/fees';
import { formatDate } from '../../../utils/format';
import { formatInstant } from '../../exams/labels';
import { PAYMENT_METHOD_LABELS, formatMoney } from '../labels';

/** While a receipt is on screen, printing shows only the receipt, in black on white. */
const printStyles = {
  '@page': { margin: '12mm' },
  '@media print': {
    body: { background: '#fff !important' },
    '.MuiAppBar-root, .MuiDrawer-root, .MuiSnackbar-root, .no-print, a[href="#main-content"]': {
      display: 'none !important',
    },
    '#main-content': { padding: '0 !important' },
    '#main-content > .MuiToolbar-root': { display: 'none !important' },
    '.receipt, .receipt *': {
      color: '#000 !important',
      background: 'transparent !important',
    },
    '.receipt .reversed-banner': { border: '3px solid #000 !important' },
  },
};

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

interface ReceiptViewProps {
  receipt: Receipt;
  /** Shown beside the print button, for example a link back to the list. */
  actions?: ReactNode;
}

/** The printable receipt of one payment. A reversed payment is clearly marked and still printable. */
export function ReceiptView({ receipt, actions }: ReceiptViewProps) {
  return (
    <Box>
      <GlobalStyles styles={printStyles} />
      <Stack
        className="no-print"
        direction="row"
        spacing={1}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap', alignItems: 'center' }}
      >
        {actions}
        <Button variant="contained" startIcon={<PrintIcon />} onClick={() => window.print()}>
          Print
        </Button>
      </Stack>

      <Paper
        className="receipt"
        component="article"
        aria-label={`Receipt ${receipt.receiptNumber}`}
        variant="outlined"
        sx={{
          maxWidth: 720,
          mx: 'auto',
          p: { xs: 2, sm: 4 },
          '@media print': { maxWidth: 'none', border: 0, boxShadow: 'none', p: 0 },
        }}
      >
        <Box sx={{ textAlign: 'center', mb: 3 }}>
          <Typography
            variant="h5"
            component="h1"
            sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}
          >
            {receipt.schoolName}
          </Typography>
          <Typography variant="h6" component="p">
            Fee receipt
          </Typography>
          <Typography component="p" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
            {receipt.receiptNumber}
          </Typography>
        </Box>

        {receipt.reversed ? (
          <Box
            className="reversed-banner"
            role="alert"
            sx={{
              mb: 3,
              p: 2,
              border: 2,
              borderColor: 'error.main',
              borderRadius: 1,
              color: 'error.main',
              textAlign: 'center',
            }}
          >
            <Typography variant="h5" component="p" sx={{ fontWeight: 800, letterSpacing: 2 }}>
              REVERSED
            </Typography>
            <Typography sx={{ overflowWrap: 'anywhere' }}>
              This payment was reversed
              {receipt.reversedAt ? ` on ${formatInstant(receipt.reversedAt)}` : ''}. It is not
              valid proof of payment.
            </Typography>
            {receipt.reversalReason ? (
              <Typography sx={{ overflowWrap: 'anywhere' }}>
                Reason: {receipt.reversalReason}
              </Typography>
            ) : null}
          </Box>
        ) : null}

        <Box
          component="dl"
          sx={{
            m: 0,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
          }}
        >
          <Fact label="Student">{receipt.studentName}</Fact>
          <Fact label="Admission no.">{receipt.admissionNumber}</Fact>
          <Fact label="Class">{receipt.className ?? '-'}</Fact>
          <Fact label="Academic year">{receipt.academicYearName}</Fact>
          <Fact label="Fee">
            {receipt.categoryName}
            {receipt.description ? ` (${receipt.description})` : ''}
          </Fact>
          <Fact label="Fee amount">{formatMoney(receipt.feeAmount)}</Fact>
          <Fact label="Payment method">{PAYMENT_METHOD_LABELS[receipt.method]}</Fact>
          <Fact label="Reference">{receipt.reference ?? '-'}</Fact>
          <Fact label="Payment date">{formatDate(receipt.paidOn)}</Fact>
          <Fact label="Received by">{receipt.receivedByName ?? '-'}</Fact>
        </Box>

        <Box
          sx={{
            mt: 3,
            p: 2,
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            display: 'grid',
            gap: 2,
            gridTemplateColumns: '1fr 1fr',
          }}
        >
          <Box component="dl" sx={{ m: 0 }}>
            <Typography component="dt" variant="body2" color="text.secondary">
              Amount received
            </Typography>
            <Typography
              component="dd"
              variant="h5"
              sx={{
                m: 0,
                fontWeight: 700,
                textDecoration: receipt.reversed ? 'line-through' : 'none',
              }}
            >
              {formatMoney(receipt.amount)}
            </Typography>
          </Box>
          <Box component="dl" sx={{ m: 0 }}>
            <Typography component="dt" variant="body2" color="text.secondary">
              Balance on this fee
            </Typography>
            <Typography component="dd" variant="h5" sx={{ m: 0, fontWeight: 700 }}>
              {formatMoney(receipt.balance)}
            </Typography>
          </Box>
        </Box>
      </Paper>
    </Box>
  );
}
