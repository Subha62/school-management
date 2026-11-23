import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { StatusChip } from '../../components/StatusChip';
import { TeacherFormDialog } from '../../features/people/components/TeacherFormDialog';
import { useTeachers } from '../../features/people/hooks';
import { EMPLOYMENT_LABELS, fullName, toOptions } from '../../features/people/labels';
import type { EmploymentStatus, Teacher } from '../../types/people';
import { formatDate } from '../../utils/format';

const ALL = 'all';

export function TeachersPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(ALL);
  const [editing, setEditing] = useState<Teacher | 'new' | null>(null);

  const teachers = useTeachers({
    page,
    size,
    search: search || undefined,
    status: status === ALL ? undefined : (status as EmploymentStatus),
  });

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Teacher>[] = [
    { id: 'employee', header: 'Employee ID', render: (t) => t.employeeId },
    { id: 'name', header: 'Name', render: (t) => fullName(t) },
    {
      id: 'subject',
      header: 'Specialization',
      hideBelow: 'md',
      render: (t) => t.specialization ?? '-',
    },
    { id: 'email', header: 'Email', hideBelow: 'lg', render: (t) => t.email },
    { id: 'joined', header: 'Joined', hideBelow: 'md', render: (t) => formatDate(t.joiningDate) },
    {
      id: 'status',
      header: 'Employment',
      render: (t) => (
        <StatusChip
          label={EMPLOYMENT_LABELS[t.employmentStatus]}
          tone={t.employmentStatus === 'ACTIVE' ? 'success' : 'default'}
        />
      ),
    },
  ];

  const actions: RowAction<Teacher>[] = [{ label: 'Edit', onSelect: setEditing }];

  return (
    <>
      <PageHeader
        title="Teachers"
        subtitle="Teacher records and sign-in accounts. Only active teachers can be assigned to sections."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New teacher
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by name or employee ID" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Employment"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All</MenuItem>
          {toOptions<EmploymentStatus>(EMPLOYMENT_LABELS).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <DataList
        data={teachers.data}
        columns={columns}
        actions={actions}
        getRowId={(teacher) => teacher.id}
        getRowLabel={(teacher) => fullName(teacher)}
        isLoading={teachers.isPending}
        error={teachers.error}
        onRetry={() => void teachers.refetch()}
        emptyTitle="No teachers found"
        emptyHint="Add a teacher, or change the search and filter."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <TeacherFormDialog
          teacher={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
    </>
  );
}
