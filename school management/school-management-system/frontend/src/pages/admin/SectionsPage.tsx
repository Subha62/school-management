import AddIcon from '@mui/icons-material/Add';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useCallback, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { SearchField } from '../../components/SearchField';
import { ActiveChip } from '../../components/StatusChip';
import { SectionFormDialog } from '../../features/academics/components/SectionFormDialog';
import {
  useDeleteSection,
  useGradeOptions,
  useSections,
  useYearOptions,
} from '../../features/academics/hooks';
import type { Section } from '../../types/academics';

const ALL = 'all';

export function SectionsPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const [gradeId, setGradeId] = useState(ALL);
  const [editing, setEditing] = useState<Section | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Section | null>(null);

  const years = useYearOptions();
  const grades = useGradeOptions();
  const activeYear = years.data?.content.find((year) => year.status === 'ACTIVE');
  // Until the user chooses, show the current (active) year, which is what administrators work on.
  const yearId = yearChoice ?? activeYear?.id ?? ALL;

  const sections = useSections({
    page,
    size,
    search: search || undefined,
    academicYearId: yearId === ALL ? undefined : yearId,
    gradeLevelId: gradeId === ALL ? undefined : gradeId,
  });
  const remove = useDeleteSection();

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Section>[] = [
    { id: 'section', header: 'Section', render: (s) => `${s.gradeLevel.name} - ${s.name}` },
    { id: 'year', header: 'Academic year', hideBelow: 'md', render: (s) => s.academicYear.name },
    { id: 'capacity', header: 'Capacity', render: (s) => s.capacity },
    {
      id: 'teacher',
      header: 'Class teacher',
      render: (s) => s.classTeacher?.name ?? 'Not assigned',
    },
    { id: 'status', header: 'Status', render: (s) => <ActiveChip active={s.active} /> },
  ];

  const isClosed = (section: Section) =>
    years.data?.content.find((year) => year.id === section.academicYear.id)?.status === 'CLOSED';

  const actions: RowAction<Section>[] = [
    { label: 'Edit', onSelect: setEditing, hidden: isClosed },
    { label: 'Delete', onSelect: setDeleting, destructive: true, hidden: isClosed },
  ];

  return (
    <>
      <PageHeader
        title="Sections"
        subtitle="Classes of a grade in an academic year, with their class teacher."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New section
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by name" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Academic year"
          value={yearId}
          onChange={(event) => {
            setYearChoice(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value={ALL}>All years</MenuItem>
          {(years.data?.content ?? []).map((year) => (
            <MenuItem key={year.id} value={year.id}>
              {year.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Grade"
          value={gradeId}
          onChange={(event) => {
            setGradeId(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value={ALL}>All grades</MenuItem>
          {(grades.data?.content ?? []).map((grade) => (
            <MenuItem key={grade.id} value={grade.id}>
              {grade.name}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <DataList
        data={sections.data}
        columns={columns}
        actions={actions}
        getRowId={(section) => section.id}
        getRowLabel={(section) => `${section.gradeLevel.name} ${section.name}`}
        isLoading={sections.isPending || years.isPending}
        error={sections.error}
        onRetry={() => void sections.refetch()}
        emptyTitle="No sections found"
        emptyHint="Create sections for a grade in an academic year, or change the filters."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {editing ? (
        <SectionFormDialog
          section={editing === 'new' ? null : editing}
          defaultYearId={yearId === ALL ? undefined : yearId}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete section?"
        message={`${deleting ? `${deleting.gradeLevel.name} ${deleting.name}` : 'This section'} will be deleted permanently. Sections with subject assignments must have them removed first.`}
        confirmLabel="Delete"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={() => {
          remove.reset();
          setDeleting(null);
        }}
        onConfirm={() => {
          if (!deleting) return;
          remove.mutate(deleting.id, {
            onSuccess: () => {
              notify.success('Section deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}
