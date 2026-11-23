import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { AuditEntry } from '../../../types/audit';
import { formatDateTime } from '../../../utils/format';
import { actionLabel, actorLabel } from '../../audit/labels';
import { Hint } from './Widgets';

/** The latest audit entries: action, summary, who and when. Plain text only. */
export function RecentActivity({ entries }: { entries: AuditEntry[] }) {
  if (entries.length === 0) {
    return <Hint>No recent activity</Hint>;
  }
  return (
    <Stack
      component="ul"
      aria-label="Recent activity entries"
      spacing={1.5}
      sx={{ listStyle: 'none', m: 0, p: 0 }}
    >
      {entries.map((entry) => (
        <Box component="li" key={entry.id} sx={{ minWidth: 0 }}>
          <Typography variant="subtitle2" component="p" sx={{ overflowWrap: 'anywhere' }}>
            {actionLabel(entry.action)}
          </Typography>
          <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
            {entry.summary}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {`${actorLabel(entry)} \u00b7 ${formatDateTime(entry.createdAt)}`}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}
