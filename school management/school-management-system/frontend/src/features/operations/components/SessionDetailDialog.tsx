import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useState } from 'react';
import { ErrorState } from '../../../components/ErrorState';
import { LoadingState } from '../../../components/LoadingState';
import { StatusChip } from '../../../components/StatusChip';
import type { AttendanceEntry } from '../../../types/operations';
import { formatDate } from '../../../utils/format';
import { useAdminSession, useCorrections } from '../hooks';
import { STATUS_LABELS, TYPE_LABELS } from '../labels';
import { AttendanceStatusChip } from './AttendanceStatusChip';
import { CorrectionDialog } from './CorrectionDialog';

interface SessionDetailDialogProps {
  sessionId: string;
  onClose: () => void;
}

/** One session for an administrator: every student's mark with a Correct action, and the correction history. */
export function SessionDetailDialog({ sessionId, onClose }: SessionDetailDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const session = useAdminSession(sessionId);
  const corrections = useCorrections(sessionId);
  const [correcting, setCorrecting] = useState<AttendanceEntry | null>(null);

  const summary = session.data?.session;
  return (
    <>
      <Dialog
        open
        onClose={onClose}
        fullScreen={fullScreen}
        fullWidth
        maxWidth="md"
        aria-labelledby="session-title"
      >
        <DialogTitle id="session-title">
          {summary
            ? `${summary.sectionName}, ${formatDate(summary.date)}${summary.subjectName ? ` · ${summary.subjectName}` : ''}`
            : 'Attendance session'}
        </DialogTitle>
        <DialogContent dividers>
          {session.isPending ? <LoadingState /> : null}
          {session.isError ? (
            <ErrorState error={session.error} onRetry={() => void session.refetch()} />
          ) : null}
          {summary && session.data ? (
            <Stack spacing={3}>
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                <StatusChip label={TYPE_LABELS[summary.type]} />
                <StatusChip
                  label={summary.status === 'FINALIZED' ? 'Finalized' : 'Open'}
                  tone={summary.status === 'FINALIZED' ? 'success' : 'warning'}
                />
              </Stack>
              <Box component="ul" aria-label="Students" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {session.data.entries.map((entry) => (
                  <Stack
                    component="li"
                    key={entry.studentId}
                    direction="row"
                    spacing={1}
                    sx={{
                      py: 1,
                      borderTop: 1,
                      borderColor: 'divider',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                    }}
                  >
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{ overflowWrap: 'anywhere' }}>{entry.name}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {entry.admissionNumber}
                        {entry.remarks ? ` · ${entry.remarks}` : ''}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexShrink: 0 }}>
                      {entry.status ? (
                        <AttendanceStatusChip status={entry.status} />
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          Not marked
                        </Typography>
                      )}
                      <Button
                        size="small"
                        aria-label={`Correct attendance of ${entry.name}`}
                        onClick={() => setCorrecting(entry)}
                      >
                        Correct
                      </Button>
                    </Stack>
                  </Stack>
                ))}
              </Box>
              <Box>
                <Typography variant="h6" component="h3" gutterBottom>
                  Corrections
                </Typography>
                {corrections.isError ? (
                  <Alert severity="error">{corrections.error.message}</Alert>
                ) : corrections.data && corrections.data.length === 0 ? (
                  <Typography color="text.secondary">No corrections have been made.</Typography>
                ) : (
                  <Box component="ul" aria-label="Corrections" sx={{ m: 0, pl: 2.5 }}>
                    {(corrections.data ?? []).map((item) => (
                      <li key={item.id}>
                        <Typography sx={{ overflowWrap: 'anywhere' }}>
                          {item.studentName}:{' '}
                          {item.previousStatus ? STATUS_LABELS[item.previousStatus] : 'Not marked'}{' '}
                          to {STATUS_LABELS[item.newStatus]}. {item.reason}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {item.correctedBy}, {new Date(item.correctedAt).toLocaleString()}
                        </Typography>
                      </li>
                    ))}
                  </Box>
                )}
              </Box>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
      {correcting ? (
        <CorrectionDialog
          sessionId={sessionId}
          entry={correcting}
          onClose={() => setCorrecting(null)}
        />
      ) : null}
    </>
  );
}
