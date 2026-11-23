import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
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
import { AssignmentFormDialog } from '../../features/assignments/components/AssignmentFormDialog';
import { AssignmentStatusChip } from '../../features/assignments/components/StatusChips';
import { useAssignments } from '../../features/assignments/hooks';
import { ASSIGNMENT_STATUS_LABELS, formatDateTime } from '../../features/assignments/labels';
import { useMyClasses } from '../../features/operations/hooks';
import type { Assignment, AssignmentStatus } from '../../types/assignments';

const ALL = 'all';

export function TeacherAssignmentListPage() {
  const navigate = useNavigate();
  const classes = useMyClasses();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [sectionId, setSectionId] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [creating, setCreating] = useState(false);

  const assignments = useAssignments({
    page,
    size,
    search: search || undefined,
    sectionId: sectionId === ALL ? undefined : sectionId,
    status: status === ALL ? undefined : (status as AssignmentStatus),
  });

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Assignment>[] = [
    {
      id: 'title',
      header: 'Title',
      render: (a) => (
        <Link component={RouterLink} to={`/teacher/assignments/${a.id}`} underline="hover">
          {a.title}
        </Link>
      ),
    },
    {
      id: 'class',
      header: 'Class and subject',
      hideBelow: 'sm',
      render: (a) => `${a.sectionName} · ${a.subjectName}`,
    },
    { id: 'due', header: 'Due', hideBelow: 'md', render: (a) => formatDateTime(a.dueAt) },
    {
      id: 'work',
      header: 'Handed in',
      hideBelow: 'md',
      render: (a) =>
        a.status === 'DRAFT' ? (
          '-'
        ) : (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <Typography component="span" variant="body2">
              {a.counts.submitted} / {a.counts.enrolled}
            </Typography>
            {a.counts.awaitingReview > 0 ? (
              <StatusChip label={`${a.counts.awaitingReview} to review`} tone="warning" />
            ) : null}
          </Stack>
        ),
    },
    { id: 'status', header: 'Status', render: (a) => <AssignmentStatusChip status={a.status} /> },
  ];
  const actions: RowAction<Assignment>[] = [
    { label: 'Open', onSelect: (a) => void navigate(`/teacher/assignments/${a.id}`) },
  ];

  return (
    <>
      <PageHeader
        title="Assignments"
        subtitle="Set work for your classes, follow what comes in, and review it."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>
            New assignment
          </Button>
        }
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
          label="Class"
          value={sectionId}
          onChange={(event) => {
            setSectionId(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value={ALL}>All classes</MenuItem>
          {(classes.data ?? [])
            .filter((option) => option.subjects.length > 0)
            .map((option) => (
              <MenuItem key={option.sectionId} value={option.sectionId}>
                {option.sectionName}
              </MenuItem>
            ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>Active (not archived)</MenuItem>
          {(Object.keys(ASSIGNMENT_STATUS_LABELS) as AssignmentStatus[]).map((value) => (
            <MenuItem key={value} value={value}>
              {ASSIGNMENT_STATUS_LABELS[value]}
            </MenuItem>
          ))}
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
        emptyTitle="No assignments yet"
        emptyHint="Create a draft, add files, and publish it when it is ready for students."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {creating ? (
        <AssignmentFormDialog
          assignment={null}
          onClose={() => setCreating(false)}
          onSaved={(saved) => void navigate(`/teacher/assignments/${saved.id}`)}
        />
      ) : null}
    </>
  );
}
