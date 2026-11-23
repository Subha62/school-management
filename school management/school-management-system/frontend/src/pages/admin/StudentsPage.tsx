import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { StatusChip } from '../../components/StatusChip';
import { StudentFormDialog } from '../../features/people/components/StudentFormDialog';
import { useStudents } from '../../features/people/hooks';
import {
  STUDENT_STATUS_LABELS,
  fullName,
  studentTone,
  toOptions,
} from '../../features/people/labels';
import type { Student, StudentStatus } from '../../types/people';

const ALL = 'all';

export function StudentsPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(ALL);
  const [editing, setEditing] = useState<Student | 'new' | null>(null);

  const students = useStudents({
    page,
    size,
    search: search || undefined,
    status: status === ALL ? undefined : (status as StudentStatus),
  });

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Student>[] = [
    { id: 'admission', header: 'Admission no.', render: (s) => s.admissionNumber },
    {
      id: 'name',
      header: 'Name',
      render: (s) => (
        <Link component={RouterLink} to={`/admin/students/${s.id}`} underline="hover">
          {fullName(s)}
        </Link>
      ),
    },
    {
      id: 'class',
      header: 'Current class',
      hideBelow: 'sm',
      render: (s) =>
        s.currentEnrollment
          ? `${s.currentEnrollment.grade} - ${s.currentEnrollment.section}`
          : 'Not enrolled',
    },
    { id: 'email', header: 'Email', hideBelow: 'lg', render: (s) => s.email },
    {
      id: 'status',
      header: 'Status',
      render: (s) => (
        <StatusChip label={STUDENT_STATUS_LABELS[s.status]} tone={studentTone(s.status)} />
      ),
    },
  ];

  const actions: RowAction<Student>[] = [
    { label: 'View', onSelect: (student) => void navigate(`/admin/students/${student.id}`) },
    { label: 'Edit', onSelect: setEditing },
  ];

  return (
    <>
      <PageHeader
        title="Students"
        subtitle="Student records and sign-in accounts. Open a student to manage guardians and enrollment."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New student
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by name or admission no." onSearch={onSearch} />
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
          <MenuItem value={ALL}>All statuses</MenuItem>
          {toOptions<StudentStatus>(STUDENT_STATUS_LABELS).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <DataList
        data={students.data}
        columns={columns}
        actions={actions}
        getRowId={(student) => student.id}
        getRowLabel={(student) => fullName(student)}
        isLoading={students.isPending}
        error={students.error}
        onRetry={() => void students.refetch()}
        emptyTitle="No students found"
        emptyHint="Add a student, or change the search and status filter."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <StudentFormDialog
          student={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
    </>
  );
}
