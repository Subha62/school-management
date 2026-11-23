import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { StatusChip } from '../../components/StatusChip';
import { SubmissionStatusChip } from '../../features/assignments/components/StatusChips';
import { useStudentAssignments } from '../../features/assignments/hooks';
import { formatDateTime, formatMarks } from '../../features/assignments/labels';
import type { StudentAssignment } from '../../types/assignments';

const ALL = 'all';

export function StudentAssignmentsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(ALL);

  const assignments = useStudentAssignments({
    page,
    size,
    search: search || undefined,
    status: status === ALL ? undefined : (status as 'PUBLISHED' | 'CLOSED'),
  });

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<StudentAssignment>[] = [
    {
      id: 'title',
      header: 'Assignment',
      render: (a) => (
        <>
          <Link component={RouterLink} to={`/student/assignments/${a.id}`} underline="hover">
            {a.title}
          </Link>
          <Typography variant="caption" color="text.secondary" component="div">
            {a.subjectName}
          </Typography>
        </>
      ),
    },
    {
      id: 'due',
      header: 'Due',
      render: (a) => (
        <Stack
          direction="row"
          spacing={0.5}
          useFlexGap
          sx={{ alignItems: 'center', flexWrap: 'wrap' }}
        >
          <span>{formatDateTime(a.dueAt)}</span>
          {a.status === 'CLOSED' ? <StatusChip label="Closed" /> : null}
          {a.status === 'PUBLISHED' && !a.submission && new Date(a.dueAt) < new Date() ? (
            <StatusChip label="Overdue" tone="warning" />
          ) : null}
        </Stack>
      ),
    },
    {
      id: 'work',
      header: 'Your work',
      render: (a) => <SubmissionStatusChip status={a.submission?.status ?? null} />,
    },
    {
      id: 'marks',
      header: 'Marks',
      hideBelow: 'sm',
      render: (a) =>
        a.submission?.status === 'REVIEWED' ? formatMarks(a.submission.marks, a.maxMarks) : '-',
    },
  ];
  const actions: RowAction<StudentAssignment>[] = [
    { label: 'Open', onSelect: (a) => void navigate(`/student/assignments/${a.id}`) },
  ];

  return (
    <>
      <PageHeader
        title="Assignments"
        subtitle="Work set for your class. Open one to read it and hand in your answer."
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by title" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Show"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All</MenuItem>
          <MenuItem value="PUBLISHED">Open</MenuItem>
          <MenuItem value="CLOSED">Closed</MenuItem>
        </TextField>
      </Stack>
      <DataList
        data={assignments.data}
        columns={columns}
        actions={actions}
        getRowId={(a) => a.id}
        getRowLabel={(a) => a.title}
        isLoading={assignments.isPending}
        error={assignments.error}
        onRetry={() => void assignments.refetch()}
        emptyTitle="No assignments"
        emptyHint="Assignments your teachers publish for your class appear here."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />
    </>
  );
}
