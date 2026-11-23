import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useEffect, useState } from 'react';
import { toApiError, type ApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import {
  MAX_REMARKS,
  toMarkEntry,
  validateMarkDraft,
  type MarkDraft,
  type MarkErrors,
} from '../../../schemas/exams';
import type { MarkEntry, MarkRow, MarksSheet } from '../../../types/exams';
import { EXAM_STATUS_LABELS, formatNumber } from '../labels';

const SERVER_ERROR_PATH = /^entries\[(\d+)\]\.(marks|remarks)$/;

const draftOf = (row: MarkRow): MarkDraft => ({
  marks: row.absent || row.marks === null ? '' : String(row.marks),
  absent: row.absent,
  remarks: row.remarks ?? '',
});

const draftsOf = (sheet: MarksSheet): Record<string, MarkDraft> =>
  Object.fromEntries(sheet.rows.map((row) => [row.studentId, draftOf(row)]));

const marksValue = (draft: MarkDraft): string => {
  const text = draft.marks.trim();
  if (draft.absent || text === '') return '';
  return Number.isNaN(Number(text)) ? text : String(Number(text));
};

const isSame = (a: MarkDraft, b: MarkDraft): boolean =>
  a.absent === b.absent && marksValue(a) === marksValue(b) && a.remarks.trim() === b.remarks.trim();

const hasEntry = (draft: MarkDraft) => draft.absent || draft.marks.trim() !== '';

interface MarksGridProps {
  sheet: MarksSheet;
  saving: boolean;
  /** Saves the changed rows and resolves with the sheet as the server now has it. */
  onSave: (entries: MarkEntry[]) => Promise<MarksSheet>;
}

/**
 * The marks of one subject in one class. Marks stay text while they are typed, are validated per row, and only
 * the rows that changed are sent. Below the `md` breakpoint every student is a card.
 */
export function MarksGrid({ sheet, saving, onSave }: MarksGridProps) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const notify = useNotify();
  const [saved, setSaved] = useState(sheet);
  const [drafts, setDrafts] = useState(() => draftsOf(sheet));
  const [serverErrors, setServerErrors] = useState<Record<string, MarkErrors>>({});
  const [failure, setFailure] = useState<ApiError | null>(null);
  const [refused, setRefused] = useState(false);
  const editable = saved.editable;

  const baselineOf = (row: MarkRow) => draftOf(row);
  const changed = saved.rows.filter((row) => {
    const draft = drafts[row.studentId];
    return draft !== undefined && !isSame(draft, baselineOf(row));
  });
  const errorsOf = (row: MarkRow): MarkErrors => {
    const draft = drafts[row.studentId];
    if (!draft || isSame(draft, baselineOf(row))) return {};
    return { ...validateMarkDraft(draft, saved.maxMarks), ...serverErrors[row.studentId] };
  };
  const invalidCount = changed.filter((row) => Object.keys(errorsOf(row)).length > 0).length;
  const dirty = changed.length > 0;
  const enteredCount = saved.rows.filter((row) => {
    const draft = drafts[row.studentId];
    return draft ? hasEntry(draft) : row.entered;
  }).length;

  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const update = (studentId: string, change: Partial<MarkDraft>) => {
    setServerErrors((current) => {
      if (!current[studentId]) return current;
      const rest = { ...current };
      delete rest[studentId];
      return rest;
    });
    setRefused(false);
    setDrafts((current) => {
      const draft = current[studentId] ?? { marks: '', absent: false, remarks: '' };
      return { ...current, [studentId]: { ...draft, ...change } };
    });
  };

  const save = async () => {
    setFailure(null);
    if (invalidCount > 0) {
      setRefused(true);
      return;
    }
    const entries = changed.map((row) => toMarkEntry(row.studentId, drafts[row.studentId]!));
    try {
      const result = await onSave(entries);
      setSaved(result);
      setDrafts(draftsOf(result));
      setServerErrors({});
      setRefused(false);
      notify.success(
        entries.length === 1
          ? 'Marks saved for 1 student'
          : `Marks saved for ${entries.length} students`,
      );
    } catch (cause) {
      const apiError = toApiError(cause);
      const mapped: Record<string, MarkErrors> = {};
      for (const [path, message] of Object.entries(apiError.fieldErrors)) {
        const match = SERVER_ERROR_PATH.exec(path);
        const studentId =
          match?.[1] === undefined ? undefined : entries[Number(match[1])]?.studentId;
        if (match && studentId) {
          mapped[studentId] = { ...mapped[studentId], [match[2] as 'marks' | 'remarks']: message };
        }
      }
      setServerErrors(mapped);
      setFailure(apiError);
    }
  };

  const nameOf = (row: MarkRow) => row.studentName;

  const marksInput = (row: MarkRow, errors: MarkErrors, labelled: boolean) => {
    const draft = drafts[row.studentId]!;
    return (
      <TextField
        size="small"
        label={labelled ? `Marks (out of ${formatNumber(saved.maxMarks)})` : undefined}
        value={draft.marks}
        disabled={draft.absent}
        onChange={(event) => update(row.studentId, { marks: event.target.value })}
        error={Boolean(errors.marks)}
        helperText={errors.marks}
        fullWidth={labelled}
        slotProps={{
          htmlInput: { inputMode: 'decimal', 'aria-label': `Marks for ${nameOf(row)}` },
        }}
        sx={labelled ? undefined : { width: 140 }}
      />
    );
  };

  const absentBox = (row: MarkRow) => {
    const draft = drafts[row.studentId]!;
    return (
      <Checkbox
        checked={draft.absent}
        onChange={(event) =>
          update(row.studentId, {
            absent: event.target.checked,
            ...(event.target.checked ? { marks: '' } : {}),
          })
        }
        sx={{ p: '10px' }}
        slotProps={{ input: { 'aria-label': `Absent: ${nameOf(row)}` } }}
      />
    );
  };

  const remarksInput = (row: MarkRow, errors: MarkErrors, labelled: boolean) => (
    <TextField
      size="small"
      label={labelled ? 'Remarks' : undefined}
      value={drafts[row.studentId]!.remarks}
      onChange={(event) => update(row.studentId, { remarks: event.target.value })}
      error={Boolean(errors.remarks)}
      helperText={errors.remarks}
      fullWidth
      slotProps={{
        htmlInput: { maxLength: MAX_REMARKS, 'aria-label': `Remarks for ${nameOf(row)}` },
      }}
    />
  );

  const readOnlyMarks = (row: MarkRow) =>
    row.absent ? 'Absent' : row.marks === null ? 'Not entered' : `${formatNumber(row.marks)}`;

  const identity = (row: MarkRow) => (
    <>
      <Typography sx={{ overflowWrap: 'anywhere', fontWeight: wide ? 400 : 600 }}>
        {row.studentName}
      </Typography>
      <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
        {row.admissionNumber}
        {!wide && row.rollNumber !== null ? ` · Roll ${row.rollNumber}` : ''}
      </Typography>
    </>
  );

  return (
    <Box>
      {!editable ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Marks are read-only because this exam is {EXAM_STATUS_LABELS[saved.status].toLowerCase()}.
          {saved.status === 'LOCKED' || saved.status === 'PUBLISHED'
            ? ' Ask an administrator if a mark needs to change.'
            : ''}
        </Alert>
      ) : null}
      {failure ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {failure.message}
        </Alert>
      ) : null}
      {refused ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {invalidCount === 1
            ? 'Fix the marks marked in red (1 student) before saving.'
            : `Fix the marks marked in red (${invalidCount} students) before saving.`}
        </Alert>
      ) : null}
      {saved.rows.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography color="text.secondary">No students are enrolled in this class.</Typography>
        </Paper>
      ) : wide ? (
        <Paper variant="outlined">
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table aria-label="Marks">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 72 }}>Roll</TableCell>
                  <TableCell>Student</TableCell>
                  <TableCell>Marks (out of {formatNumber(saved.maxMarks)})</TableCell>
                  <TableCell>Absent</TableCell>
                  <TableCell>Remarks</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {saved.rows.map((row) => {
                  const errors = errorsOf(row);
                  return (
                    <TableRow key={row.studentId} hover>
                      <TableCell>{row.rollNumber ?? '-'}</TableCell>
                      <TableCell sx={{ overflowWrap: 'anywhere' }}>{identity(row)}</TableCell>
                      {editable ? (
                        <>
                          <TableCell sx={{ verticalAlign: 'top' }}>
                            {marksInput(row, errors, false)}
                          </TableCell>
                          <TableCell sx={{ verticalAlign: 'top' }}>{absentBox(row)}</TableCell>
                          <TableCell sx={{ verticalAlign: 'top', minWidth: 220 }}>
                            {remarksInput(row, errors, false)}
                          </TableCell>
                        </>
                      ) : (
                        <>
                          <TableCell>{readOnlyMarks(row)}</TableCell>
                          <TableCell>{row.absent ? 'Yes' : 'No'}</TableCell>
                          <TableCell sx={{ overflowWrap: 'anywhere' }}>
                            {row.remarks ?? ''}
                          </TableCell>
                        </>
                      )}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      ) : (
        <Stack
          component="ul"
          aria-label="Marks"
          spacing={1.5}
          sx={{ listStyle: 'none', m: 0, p: 0 }}
        >
          {saved.rows.map((row) => {
            const errors = errorsOf(row);
            return (
              <Card key={row.studentId} component="li" variant="outlined">
                <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                  <Box sx={{ minWidth: 0, mb: 1.5 }}>{identity(row)}</Box>
                  {editable ? (
                    <Stack spacing={1.5}>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                        <Box sx={{ flex: 1, minWidth: 0 }}>{marksInput(row, errors, true)}</Box>
                        <FormControlLabel
                          label="Absent"
                          sx={{ m: 0, minHeight: 44, flexShrink: 0 }}
                          control={absentBox(row)}
                        />
                      </Stack>
                      {remarksInput(row, errors, true)}
                    </Stack>
                  ) : (
                    <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                      {readOnlyMarks(row)}
                      {row.remarks ? ` · ${row.remarks}` : ''}
                    </Typography>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </Stack>
      )}

      {editable && saved.rows.length > 0 ? (
        <Paper
          elevation={3}
          sx={{
            position: 'sticky',
            bottom: 0,
            mt: 2,
            p: 1.5,
            display: 'flex',
            flexWrap: 'wrap',
            gap: 1.5,
            alignItems: 'center',
            zIndex: 1,
          }}
        >
          <Button
            variant="contained"
            loading={saving}
            disabled={!dirty}
            onClick={() => void save()}
          >
            Save marks
          </Button>
          <Typography
            variant="body2"
            color={dirty ? 'warning.main' : 'text.secondary'}
            role="status"
          >
            {dirty
              ? `Unsaved changes for ${changed.length} ${changed.length === 1 ? 'student' : 'students'}`
              : 'All changes saved'}
            {` · ${enteredCount} of ${saved.rows.length} entered`}
          </Typography>
        </Paper>
      ) : null}
    </Box>
  );
}
