import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { ReviewDialog } from '../../features/assignments/components/ReviewDialog';
import { SubmissionStatusChip } from '../../features/assignments/components/StatusChips';
import { useReviewQueue } from '../../features/assignments/hooks';
import {
  SUBMISSION_STATUS_LABELS,
  formatDateTime,
  formatMarks,
} from '../../features/assignments/labels';
import type { SubmissionListItem, SubmissionStatus } from '../../types/assignments';

const PENDING = 'pending';
const ALL = 'all';

export function TeacherSubmissionsPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [filter, setFilter] = useState(PENDING);
  const [reviewing, setReviewing] = useState<SubmissionListItem | null>(null);

  const queue = useReviewQueue({
    page,
    size,
    pending: filter === PENDING,
    status: filter === PENDING || filter === ALL ? undefined : (filter as SubmissionStatus),
  });

  const columns: Column<SubmissionListItem>[] = [
    {
      id: 'student',
      header: 'Student',
      render: (s) => `${s.studentName} (${s.admissionNumber})`,
    },
    {
      id: 'assignment',
      header: 'Assignment',
      render: (s) => (
        <>
          {s.assignmentTitle}
          <Typography variant="caption" color="text.secondary" component="div">
            {s.sectionName} · {s.subjectName}
          </Typography>
        </>
      ),
    },
    {
      id: 'when',
      header: 'Handed in',
      hideBelow: 'md',
      render: (s) => formatDateTime(s.submittedAt),
    },
    {
      id: 'marks',
      header: 'Marks',
      hideBelow: 'sm',
      render: (s) => (s.status === 'REVIEWED' ? formatMarks(s.marks, s.maxMarks) : '-'),
    },
    { id: 'status', header: 'Status', render: (s) => <SubmissionStatusChip status={s.status} /> },
  ];
  const actions: RowAction<SubmissionListItem>[] = [
    {
      label: 'Review',
      onSelect: setReviewing,
      hidden: (s) => s.status === 'RESUBMISSION_REQUESTED',
    },
  ];

  return (
    <>
      <PageHeader
        title="Submission review"
        subtitle="Work handed in to your assignments, oldest first."
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <TextField
          select
          size="small"
          label="Show"
          value={filter}
          onChange={(event) => {
            setFilter(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value={PENDING}>Waiting for review</MenuItem>
          <MenuItem value={ALL}>All submissions</MenuItem>
          {(Object.keys(SUBMISSION_STATUS_LABELS) as SubmissionStatus[]).map((value) => (
            <MenuItem key={value} value={value}>
              {SUBMISSION_STATUS_LABELS[value]}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <DataList
        data={queue.data}
        columns={columns}
        actions={actions}
        getRowId={(s) => s.id}
        getRowLabel={(s) => `${s.studentName} ${s.assignmentTitle}`}
        isLoading={queue.isPending}
        error={queue.error}
        onRetry={() => void queue.refetch()}
        emptyTitle={filter === PENDING ? 'Nothing is waiting for review' : 'No submissions'}
        emptyHint="Work appears here as students hand it in."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
      {reviewing ? (
        <ReviewDialog
          assignmentId={reviewing.assignmentId}
          submissionId={reviewing.id}
          maxMarks={reviewing.maxMarks}
          onClose={() => setReviewing(null)}
        />
      ) : null}
    </>
  );
}
