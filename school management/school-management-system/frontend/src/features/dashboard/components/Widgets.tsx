import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useId, type ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { StatusChip } from '../../../components/StatusChip';
import type { DashboardSlot } from '../../../types/dashboard';
import { shortTime } from '../../../utils/dates';

interface WidgetProps {
  title: string;
  children: ReactNode;
  /** A link shown under the content, e.g. "View all". */
  link?: { to: string; label: string };
  /** Spans two columns on large screens. */
  wide?: boolean;
}

/** One dashboard card: a labelled region with a heading, its content and an optional link. */
export function Widget({ title, children, link, wide = false }: WidgetProps) {
  const headingId = useId();
  return (
    <Grid size={{ xs: 12, sm: 6, lg: wide ? 8 : 4 }} sx={{ minWidth: 0 }}>
      <Card
        component="section"
        variant="outlined"
        aria-labelledby={headingId}
        sx={{ height: '100%' }}
      >
        <CardContent sx={{ height: '100%', display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Typography id={headingId} variant="h6" component="h2" sx={{ overflowWrap: 'anywhere' }}>
            {title}
          </Typography>
          <Box sx={{ flexGrow: 1, minWidth: 0 }}>{children}</Box>
          {link ? (
            <Box>
              <Button
                component={RouterLink}
                to={link.to}
                size="small"
                sx={{ minHeight: 44, minWidth: 44 }}
              >
                {link.label}
              </Button>
            </Box>
          ) : null}
        </CardContent>
      </Card>
    </Grid>
  );
}

export interface StatItem {
  label: string;
  value: string | number;
}

/** Label/value pairs; values stay readable at any width. */
export function Stats({ items, label }: { items: StatItem[]; label?: string }) {
  return (
    <Box
      component="dl"
      aria-label={label}
      sx={{
        m: 0,
        display: 'grid',
        gap: 1.5,
        gridTemplateColumns: `repeat(${Math.min(items.length, 2)}, minmax(0, 1fr))`,
      }}
    >
      {items.map((item) => (
        <Box key={item.label} sx={{ minWidth: 0 }}>
          <Typography component="dt" variant="body2" color="text.secondary">
            {item.label}
          </Typography>
          <Typography component="dd" variant="h5" sx={{ m: 0, overflowWrap: 'anywhere' }}>
            {item.value}
          </Typography>
        </Box>
      ))}
    </Box>
  );
}

/** A single prominent number with a caption. */
export function BigCount({
  value,
  caption,
  format = String,
}: {
  value: number;
  caption: string;
  format?: (value: number) => string;
}) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="h3" component="p" sx={{ m: 0 }}>
        {format(value)}
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {caption}
      </Typography>
    </Stack>
  );
}

export function Hint({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
      {children}
    </Typography>
  );
}

/** Today's periods in order. */
export function SlotList({ slots, label }: { slots: DashboardSlot[]; label: string }) {
  if (slots.length === 0) {
    return <Hint>No periods today</Hint>;
  }
  return (
    <Stack component="ul" aria-label={label} spacing={1} sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {slots.map((slot) => (
        <Box
          component="li"
          key={`${slot.periodNumber}-${slot.startTime}-${slot.sectionName}`}
          sx={{ minWidth: 0 }}
        >
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <StatusChip label={`Period ${slot.periodNumber}`} />
            <Typography variant="body2" color="text.secondary">
              {shortTime(slot.startTime)} - {shortTime(slot.endTime)}
            </Typography>
          </Stack>
          <Typography variant="body1" sx={{ overflowWrap: 'anywhere' }}>
            {slot.subjectName}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {slot.sectionName}
            {slot.room ? ` \u00b7 Room ${slot.room}` : ''}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}
