import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import type { FeeSummary } from '../../../types/fees';
import { formatMoney } from '../labels';

interface Card {
  label: string;
  value: number;
  emphasis?: boolean;
}

/** Billed, collected and outstanding amounts, exactly as the server reports them. */
export function FeeSummaryCards({ summary }: { summary: FeeSummary }) {
  const cards: Card[] = [
    { label: 'Billed', value: summary.billed },
    { label: 'Collected', value: summary.collected },
    { label: 'Outstanding', value: summary.outstanding },
    { label: 'Overdue', value: summary.overdue, emphasis: summary.overdue > 0 },
    { label: 'Waived', value: summary.waived },
  ];
  return (
    <Box
      component="dl"
      aria-label="Fee summary"
      sx={{
        m: 0,
        mb: 2,
        display: 'grid',
        gap: 1.5,
        gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(5, 1fr)' },
      }}
    >
      {cards.map((card) => (
        <Paper key={card.label} variant="outlined" sx={{ p: 2, minWidth: 0 }}>
          <Typography component="dt" variant="body2" color="text.secondary">
            {card.label}
          </Typography>
          <Typography
            component="dd"
            variant="h6"
            sx={{
              m: 0,
              overflowWrap: 'anywhere',
              color: card.emphasis ? 'warning.dark' : 'text.primary',
            }}
          >
            {formatMoney(card.value)}
          </Typography>
        </Paper>
      ))}
    </Box>
  );
}
