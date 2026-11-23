import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { ApiError } from '../../../api/errors';
import { StatusChip } from '../../../components/StatusChip';
import type { AttendanceDetail, AttendanceStatus, MarkInput } from '../../../types/operations';
import { formatDate } from '../../../utils/format';
import { STATUS_LABELS, STATUS_ORDER, TYPE_LABELS } from '../labels';
import { AttendanceStatusChip } from './AttendanceStatusChip';

interface Mark {
  status: AttendanceStatus | null;
  remarks: string;
}

interface AttendanceSheetProps {
  detail: AttendanceDetail;
  saving: boolean;
  finalizing: boolean;
  error: ApiError | null;
  onSave: (records: MarkInput[]) => void;
  onFinalize: () => void;
  onDelete: () => void;
}

/** The register of one session: a status per student, saved together, then finalized. */
export function AttendanceSheet({
  detail,
  saving,
  finalizing,
  error,
  onSave,
  onFinalize,
  onDelete,
}: AttendanceSheetProps) {
  const { session, entries } = detail;
  const finalized = session.status === 'FINALIZED';
  const [marks, setMarks] = useState<Record<string, Mark>>(() =>
    Object.fromEntries(
      entries.map((entry) => [
        entry.studentId,
        { status: entry.status, remarks: entry.remarks ?? '' },
      ]),
    ),
  );

  const markOf = (studentId: string): Mark => marks[studentId] ?? { status: null, remarks: '' };
  const markedLocally = entries.filter((entry) => markOf(entry.studentId).status !== null).length;
  const markedOnServer = entries.filter((entry) => entry.status !== null).length;
  const dirty = entries.some((entry) => {
    const mark = markOf(entry.studentId);
    return mark.status !== entry.status || mark.remarks !== (entry.remarks ?? '');
  });

  const update = (studentId: string, change: Partial<Mark>) =>
    setMarks((current) => ({ ...current, [studentId]: { ...markOf(studentId), ...change } }));

  const save = () =>
    onSave(
      entries
        .filter((entry) => markOf(entry.studentId).status !== null)
        .map((entry) => {
          const mark = markOf(entry.studentId);
          return {
            studentId: entry.studentId,
            status: mark.status as AttendanceStatus,
            remarks: mark.remarks.trim() === '' ? null : mark.remarks.trim(),
          };
        }),
    );

  const title = `${session.sectionName}, ${formatDate(session.date)}${
    session.subjectName ? ` · ${session.subjectName}` : ''
  }${session.periodNumber ? ` · period ${session.periodNumber}` : ''}`;

  return (
    <Paper variant="outlined" sx={{ p: { xs: 1.5, sm: 2 } }}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={1}
        sx={{ justifyContent: 'space-between', alignItems: { md: 'center' }, mb: 2 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h6" component="h2" sx={{ overflowWrap: 'anywhere' }}>
            {title}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 0.5, alignItems: 'center' }}>
            <StatusChip label={TYPE_LABELS[session.type]} />
            <StatusChip
              label={finalized ? 'Finalized' : 'Open'}
              tone={finalized ? 'success' : 'warning'}
            />
            <Typography variant="body2" color="text.secondary">
              Marked {finalized ? markedOnServer : markedLocally} of {entries.length}
            </Typography>
          </Stack>
        </Box>
        {!finalized ? (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Button
              onClick={() =>
                setMarks(
                  Object.fromEntries(
                    entries.map((entry) => [
                      entry.studentId,
                      {
                        ...markOf(entry.studentId),
                        status: markOf(entry.studentId).status ?? 'PRESENT',
                      },
                    ]),
                  ),
                )
              }
            >
              Mark unmarked as present
            </Button>
            <Button
              variant="contained"
              loading={saving}
              disabled={!dirty || markedLocally === 0}
              onClick={save}
            >
              Save marks
            </Button>
            <Button
              variant="outlined"
              loading={finalizing}
              disabled={dirty || entries.length === 0 || markedOnServer < entries.length}
              onClick={onFinalize}
            >
              Finalize
            </Button>
          </Stack>
        ) : null}
      </Stack>

      {error ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error.message}
        </Alert>
      ) : null}
      {finalized ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          This attendance is finalized. Only an administrator can correct it.
        </Alert>
      ) : dirty ? (
        <Alert severity="warning" sx={{ mb: 2 }}>
          You have unsaved changes. Save them before finalizing.
        </Alert>
      ) : null}
      {entries.length === 0 ? (
        <Typography color="text.secondary">No students are enrolled in this class.</Typography>
      ) : (
        <Box component="ul" aria-label="Students" sx={{ listStyle: 'none', m: 0, p: 0 }}>
          {entries.map((entry) => {
            const mark = markOf(entry.studentId);
            return (
              <Stack
                component="li"
                key={entry.studentId}
                direction={{ xs: 'column', md: 'row' }}
                spacing={1}
                sx={{
                  py: 1.25,
                  borderTop: 1,
                  borderColor: 'divider',
                  alignItems: { md: 'center' },
                }}
              >
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ overflowWrap: 'anywhere' }}>
                    {entry.rollNumber ? `${entry.rollNumber}. ` : ''}
                    {entry.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {entry.admissionNumber}
                  </Typography>
                </Box>
                {finalized ? (
                  entry.status ? (
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      <AttendanceStatusChip status={entry.status} />
                      {entry.remarks ? (
                        <Typography variant="body2" color="text.secondary">
                          {entry.remarks}
                        </Typography>
                      ) : null}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      Not marked
                    </Typography>
                  )
                ) : (
                  <Stack spacing={1} sx={{ minWidth: 0 }}>
                    <ToggleButtonGroup
                      exclusive
                      size="small"
                      value={mark.status}
                      aria-label={`Attendance of ${entry.name}`}
                      onChange={(_event, value: AttendanceStatus | null) =>
                        update(entry.studentId, { status: value })
                      }
                      sx={{ flexWrap: 'wrap' }}
                    >
                      {STATUS_ORDER.map((status) => (
                        <ToggleButton
                          key={status}
                          value={status}
                          aria-label={`${STATUS_LABELS[status]} for ${entry.name}`}
                          sx={{ minHeight: 44, px: 1.5 }}
                        >
                          {STATUS_LABELS[status]}
                        </ToggleButton>
                      ))}
                    </ToggleButtonGroup>
                    {mark.status !== null && mark.status !== 'PRESENT' ? (
                      <TextField
                        size="small"
                        label="Remarks"
                        value={mark.remarks}
                        onChange={(event) =>
                          update(entry.studentId, { remarks: event.target.value })
                        }
                        slotProps={{
                          htmlInput: {
                            maxLength: 200,
                            'aria-label': `Remarks for ${entry.name}`,
                          },
                        }}
                      />
                    ) : null}
                  </Stack>
                )}
              </Stack>
            );
          })}
        </Box>
      )}
      {!finalized ? (
        <Button color="error" size="small" sx={{ mt: 2 }} onClick={onDelete}>
          Delete this session
        </Button>
      ) : null}
    </Paper>
  );
}
