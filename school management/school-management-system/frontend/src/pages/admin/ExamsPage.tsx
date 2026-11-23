import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { useGradeOptions, useYearOptions } from '../../features/academics/hooks';
import { ExamFormDialog } from '../../features/exams/components/ExamFormDialog';
import { ExamStatusChip } from '../../features/exams/components/StatusChips';
import { useDeleteExam, useExams } from '../../features/exams/hooks';
import {
  EXAM_STATUS_LABELS,
  EXAM_STATUS_ORDER,
  formatDateRange,
} from '../../features/exams/labels';
import type { Exam, ExamStatus } from '../../types/exams';
import { formatDate } from '../../utils/format';

const ALL = 'all';

export function ExamsPage() {
  const navigate = useNavigate();
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [yearId, setYearId] = useState(ALL);
  const [gradeId, setGradeId] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [editing, setEditing] = useState<Exam | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Exam | null>(null);

  const exams = useExams({
    page,
    size,
    sort: 'createdAt',
    direction: 'desc',
    search: search || undefined,
    academicYearId: yearId === ALL ? undefined : yearId,
    gradeLevelId: gradeId === ALL ? undefined : gradeId,
    status: status === ALL ? undefined : (status as ExamStatus),
  });
  const remove = useDeleteExam();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);
  const filtered = search !== '' || yearId !== ALL || gradeId !== ALL || status !== ALL;

  const columns: Column<Exam>[] = [
    {
      id: 'name',
      header: 'Exam',
      render: (exam) => (
        <Link component={RouterLink} to={`/admin/exams/${exam.id}`} underline="hover">
          {exam.name}
        </Link>
      ),
    },
    {
      id: 'class',
      header: 'Year and grade',
      render: (e) => `${e.academicYearName} · ${e.gradeLevelName}`,
    },
    {
      id: 'dates',
      header: 'Dates',
      hideBelow: 'lg',
      render: (e) => formatDateRange(e.startDate, e.endDate, formatDate),
    },
    { id: 'subjects', header: 'Subjects', hideBelow: 'md', render: (e) => e.subjects.length },
    { id: 'status', header: 'Status', render: (e) => <ExamStatusChip status={e.status} /> },
  ];
  const actions: RowAction<Exam>[] = [
    { label: 'Open', onSelect: (exam) => void navigate(`/admin/exams/${exam.id}`) },
    { label: 'Edit', onSelect: setEditing, hidden: (exam) => exam.status !== 'DRAFT' },
    {
      label: 'Delete',
      onSelect: setDeleting,
      destructive: true,
      hidden: (exam) => exam.status !== 'DRAFT',
    },
  ];

  const cancelDelete = () => {
    remove.reset();
    setDeleting(null);
  };

  const filter = (
    label: string,
    value: string,
    onChange: (value: string) => void,
    options: { value: string; label: string }[],
    allLabel: string,
  ) => (
    <TextField
      select
      size="small"
      label={label}
      value={value}
      onChange={(event) => {
        onChange(event.target.value);
        setPage(0);
      }}
      sx={{ minWidth: 170 }}
    >
      <MenuItem value={ALL}>{allLabel}</MenuItem>
      {options.map((option) => (
        <MenuItem key={option.value} value={option.value}>
          {option.label}
        </MenuItem>
      ))}
    </TextField>
  );

  return (
    <>
      <PageHeader
        title="Exams"
        subtitle="Set up an exam for a grade, open it for marks, lock it, and publish the results."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New exam
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap' }}
      >
        <SearchField label="Search by name" onSearch={onSearch} />
        {filter(
          'Academic year',
          yearId,
          setYearId,
          (years.data?.content ?? []).map((year) => ({ value: year.id, label: year.name })),
          'All years',
        )}
        {filter(
          'Grade',
          gradeId,
          setGradeId,
          (grades.data?.content ?? []).map((grade) => ({ value: grade.id, label: grade.name })),
          'All grades',
        )}
        {filter(
          'Status',
          status,
          setStatus,
          EXAM_STATUS_ORDER.map((value) => ({ value, label: EXAM_STATUS_LABELS[value] })),
          'All statuses',
        )}
      </Stack>

      <DataList
        data={exams.data}
        columns={columns}
        actions={actions}
        getRowId={(exam) => exam.id}
        getRowLabel={(exam) => exam.name}
        isLoading={exams.isPending}
        error={exams.error}
        onRetry={() => void exams.refetch()}
        emptyTitle="No exams found"
        emptyHint={
          filtered ? 'Try a different search or filter.' : 'Create the first exam for a grade.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <ExamFormDialog
          exam={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete exam?"
        message={`${deleting?.name ?? 'This exam'} is deleted permanently. Only draft exams can be deleted.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={cancelDelete}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Exam deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
