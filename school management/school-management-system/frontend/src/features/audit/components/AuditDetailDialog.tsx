import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { StatusChip } from '../../../components/StatusChip';
import type { AuditEntry } from '../../../types/audit';
import { formatDateTime } from '../../../utils/format';
import { actionLabel, actorLabel, diffRows, entityTypeLabel } from '../labels';

const wrap = { overflowWrap: 'anywhere', minWidth: 0 } as const;

function Field({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, ...wrap }}>
        {value}
      </Typography>
    </Box>
  );
}

function ChangesTable({ entry }: { entry: AuditEntry }) {
  const rows = diffRows(entry.oldValues, entry.newValues);
  if (rows.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No values were recorded for this entry.
      </Typography>
    );
  }
  return (
    <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
      <Table size="small" aria-label="Before / After" sx={{ tableLayout: 'fixed' }}>
        <TableHead>
          <TableRow>
            <TableCell component="th" scope="col" sx={{ width: '28%' }}>
              Field
            </TableCell>
            <TableCell component="th" scope="col">
              Before
            </TableCell>
            <TableCell component="th" scope="col">
              After
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={row.field}
              data-changed={row.changed}
              sx={row.changed ? { bgcolor: 'warning.light' } : undefined}
            >
              <TableCell component="th" scope="row" sx={wrap}>
                <Stack spacing={0.5} sx={{ alignItems: 'flex-start' }}>
                  <span>{row.field}</span>
                  {row.changed ? <StatusChip label="Changed" tone="warning" /> : null}
                </Stack>
              </TableCell>
              <TableCell sx={wrap}>{row.before}</TableCell>
              <TableCell sx={wrap}>{row.after}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  );
}

/** Read-only view of one audit entry. The trail is append-only, so there is nothing to edit here. */
export function AuditDetailDialog({ entry, onClose }: { entry: AuditEntry; onClose: () => void }) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));

  return (
    <Dialog
      open
      onClose={onClose}
      fullScreen={fullScreen}
      fullWidth
      maxWidth="md"
      aria-labelledby="audit-detail-title"
    >
      <DialogTitle id="audit-detail-title" sx={wrap}>
        Audit log details
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3}>
          <Box
            component="dl"
            sx={{
              m: 0,
              display: 'grid',
              gap: 2,
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
            }}
          >
            <Field label="Time" value={formatDateTime(entry.createdAt)} />
            <Field label="Action" value={actionLabel(entry.action)} />
            <Field label="Action code" value={entry.action} />
            <Field label="Actor" value={actorLabel(entry)} />
            <Field label="Entity type" value={entityTypeLabel(entry.entityType)} />
            <Field label="Entity ID" value={entry.entityId || '\u2014'} />
            <Field label="IP address" value={entry.ipAddress || '\u2014'} />
            <Field label="User agent" value={entry.userAgent || '\u2014'} />
            <Box sx={{ gridColumn: { sm: '1 / -1' }, minWidth: 0 }}>
              <Field label="Summary" value={entry.summary} />
            </Box>
          </Box>
          <Box>
            <Typography variant="h6" component="h3" gutterBottom>
              Before / After
            </Typography>
            <ChangesTable entry={entry} />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
