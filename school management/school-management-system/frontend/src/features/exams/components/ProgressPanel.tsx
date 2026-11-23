import RefreshIcon from '@mui/icons-material/Refresh';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { UseQueryResult } from '@tanstack/react-query';
import type { ApiError } from '../../../api/errors';
import { ErrorState } from '../../../components/ErrorState';
import { LoadingState } from '../../../components/LoadingState';
import type { Progress, ProgressRow } from '../../../types/exams';
import { ResponsiveRows, type RowColumn } from './ResponsiveRows';
import { StatusChip } from '../../../components/StatusChip';

const percentOf = (row: ProgressRow) =>
  row.expected === 0 ? 100 : Math.min(100, (row.entered / row.expected) * 100);

const columns: RowColumn<ProgressRow>[] = [
  { id: 'subject', header: 'Subject', render: (row) => row.subjectName },
  { id: 'class', header: 'Class', render: (row) => row.sectionName },
  { id: 'teacher', header: 'Teacher', render: (row) => row.teacherName ?? 'Not assigned' },
  {
    id: 'entered',
    header: 'Entered',
    render: (row) => (
      <Box sx={{ minWidth: { md: 160 } }}>
        <Typography variant="body2">
          {row.entered} of {row.expected}
        </Typography>
        <LinearProgress
          variant="determinate"
          value={percentOf(row)}
          color={row.entered >= row.expected ? 'success' : 'primary'}
          aria-label={`Marks entered for ${row.subjectName}, ${row.sectionName}`}
          sx={{ height: 8, borderRadius: 4, mt: 0.5 }}
        />
      </Box>
    ),
  },
  {
    id: 'state',
    header: 'State',
    render: (row) =>
      row.entered >= row.expected ? (
        <StatusChip label="Complete" tone="success" />
      ) : (
        <StatusChip label={`${row.expected - row.entered} missing`} tone="warning" />
      ),
  },
];

/** How much marks entry is done per subject and class, for the exam detail page. */
export function ProgressPanel({ progress }: { progress: UseQueryResult<Progress, ApiError> }) {
  if (progress.error && !progress.data) {
    return (
      <ErrorState
        title="Unable to load the progress"
        error={progress.error}
        onRetry={() => void progress.refetch()}
      />
    );
  }
  if (!progress.data) return <LoadingState label="Loading progress" />;

  const { complete, missing, rows } = progress.data;
  return (
    <Stack spacing={2}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
        sx={{ alignItems: { sm: 'center' } }}
      >
        <Alert severity={complete ? 'success' : 'warning'} sx={{ flex: 1 }}>
          {complete
            ? 'All marks are entered. The exam can be locked.'
            : `${missing} ${missing === 1 ? 'entry is' : 'entries are'} still missing. The exam cannot be locked until every mark or absence is entered.`}
        </Alert>
        <Button
          startIcon={<RefreshIcon />}
          loading={progress.isFetching}
          onClick={() => void progress.refetch()}
        >
          Refresh
        </Button>
      </Stack>
      {rows.length === 0 ? (
        <Typography color="text.secondary">
          No classes are linked to this exam yet. Assign teachers to the subjects of its grade.
        </Typography>
      ) : (
        <ResponsiveRows
          label="Marks entry progress"
          rows={rows}
          columns={columns}
          getRowId={(row) => `${row.examSubjectId}-${row.sectionId}`}
        />
      )}
    </Stack>
  );
}
