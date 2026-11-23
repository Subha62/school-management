import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column, type RowAction } from '../../components/DataList';
import { PageHeader } from '../../components/PageHeader';
import { ActiveChip, StatusChip } from '../../components/StatusChip';
import { AssignmentFormDialog } from '../../features/academics/components/AssignmentFormDialog';
import {
  useAssignments,
  useDeleteAssignment,
  useSectionOptions,
  useTeacherOptions,
  useYearOptions,
} from '../../features/academics/hooks';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import type { SectionSubject, TeacherOption } from '../../types/academics';

const ALL = 'all';

export function TeacherAssignmentsPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const [sectionId, setSectionId] = useState(ALL);
  const [teacher, setTeacher] = useState<TeacherOption | null>(null);
  const [teacherInput, setTeacherInput] = useState('');
  const [editing, setEditing] = useState<SectionSubject | 'new' | null>(null);
  const [removing, setRemoving] = useState<SectionSubject | null>(null);

  const years = useYearOptions();
  const activeYear = years.data?.content.find((year) => year.status === 'ACTIVE');
  const yearId = yearChoice ?? activeYear?.id ?? years.data?.content[0]?.id;
  const year = years.data?.content.find((candidate) => candidate.id === yearId);
  const yearClosed = year?.status === 'CLOSED';

  const sections = useSectionOptions(yearId);
  const sectionOptions = sections.data?.content ?? [];
  const selectedSection = sectionOptions.find((section) => section.id === sectionId);
  const teacherSearch = useDebouncedValue(teacherInput.trim(), 300);
  const teachers = useTeacherOptions(teacherSearch);

  const assignments = useAssignments(
    {
      page,
      size,
      academicYearId: yearId,
      sectionId: sectionId === ALL ? undefined : sectionId,
      teacherId: teacher?.id,
    },
    Boolean(yearId),
  );
  const remove = useDeleteAssignment();

  const columns: Column<SectionSubject>[] = [
    { id: 'section', header: 'Section', render: (a) => `${a.gradeLevelName} - ${a.sectionName}` },
    { id: 'subject', header: 'Subject', render: (a) => `${a.subject.code} - ${a.subject.name}` },
    {
      id: 'teacher',
      header: 'Teacher',
      render: (a) =>
        a.teacher ? a.teacher.name : <StatusChip label="Unassigned" tone="warning" />,
    },
    { id: 'periods', header: 'Periods / week', render: (a) => a.weeklyPeriods },
    { id: 'status', header: 'Status', render: (a) => <ActiveChip active={a.active} /> },
  ];

  const actions: RowAction<SectionSubject>[] = yearClosed
    ? []
    : [
        { label: 'Edit', onSelect: setEditing },
        { label: 'Remove', onSelect: setRemoving, destructive: true },
      ];

  const resetPaging = () => setPage(0);

  return (
    <>
      <PageHeader
        title="Teacher assignments"
        subtitle="Choose which subjects each section studies and who teaches them."
        actions={
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            disabled={!selectedSection || yearClosed}
            onClick={() => setEditing('new')}
          >
            Assign subject
          </Button>
        }
      />
      {yearClosed ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          {year?.name} is closed, so its assignments are read-only.
        </Alert>
      ) : null}
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <TextField
          select
          size="small"
          label="Academic year"
          value={yearId ?? ''}
          onChange={(event) => {
            setYearChoice(event.target.value);
            setSectionId(ALL);
            resetPaging();
          }}
          sx={{ minWidth: 180 }}
        >
          {(years.data?.content ?? []).map((candidate) => (
            <MenuItem key={candidate.id} value={candidate.id}>
              {candidate.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Section"
          value={sectionId}
          onChange={(event) => {
            setSectionId(event.target.value);
            resetPaging();
          }}
          sx={{ minWidth: 200 }}
          helperText={selectedSection ? undefined : 'Pick a section to assign subjects'}
        >
          <MenuItem value={ALL}>All sections</MenuItem>
          {sectionOptions.map((section) => (
            <MenuItem key={section.id} value={section.id}>
              {section.gradeLevel.name} - {section.name}
            </MenuItem>
          ))}
        </TextField>
        <Autocomplete<TeacherOption>
          size="small"
          sx={{ minWidth: 260, flexGrow: { md: 1 }, maxWidth: { md: 380 } }}
          options={teachers.data?.content ?? []}
          value={teacher}
          loading={teachers.isFetching}
          getOptionLabel={(option) => `${option.firstName} ${option.lastName}`}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          filterOptions={(all) => all}
          onChange={(_event, value) => {
            setTeacher(value);
            resetPaging();
          }}
          onInputChange={(_event, value, reason) => {
            if (reason === 'input') setTeacherInput(value);
          }}
          renderInput={(params) => <TextField {...params} label="Filter by teacher" />}
        />
      </Stack>

      <DataList
        data={assignments.data}
        columns={columns}
        actions={actions}
        getRowId={(a) => a.id}
        getRowLabel={(a) => `${a.subject.name} in ${a.gradeLevelName} ${a.sectionName}`}
        isLoading={assignments.isPending && Boolean(yearId)}
        error={assignments.error}
        onRetry={() => void assignments.refetch()}
        emptyTitle="No subject assignments"
        emptyHint={
          selectedSection
            ? 'Use "Assign subject" to add the first subject to this section.'
            : 'Pick a section to see and assign its subjects.'
        }
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          resetPaging();
        }}
      />

      {editing && (editing !== 'new' || selectedSection) ? (
        <AssignmentFormDialog
          assignment={editing === 'new' ? null : editing}
          sectionId={editing === 'new' ? (selectedSection?.id ?? '') : editing.sectionId}
          sectionLabel={
            editing === 'new'
              ? `${selectedSection?.gradeLevel.name ?? ''} - ${selectedSection?.name ?? ''}`
              : `${editing.gradeLevelName} - ${editing.sectionName}`
          }
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={removing !== null}
        title="Remove subject from section?"
        message={`${removing?.subject.name ?? 'This subject'} will no longer be taught in ${removing ? `${removing.gradeLevelName} ${removing.sectionName}` : 'the section'}.`}
        confirmLabel="Remove"
        destructive
        loading={remove.isPending}
        error={remove.error}
        onCancel={() => {
          remove.reset();
          setRemoving(null);
        }}
        onConfirm={() => {
          if (!removing) return;
          remove.mutate(removing.id, {
            onSuccess: () => {
              notify.success('Subject removed');
              setRemoving(null);
            },
          });
        }}
      />
    </>
  );
}
