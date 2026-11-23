import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { useSectionOptions, useYearOptions } from '../../features/academics/hooks';
import { TeacherPicker } from '../../features/operations/components/TeacherPicker';
import { TimetableEntryDialog } from '../../features/operations/components/TimetableEntryDialog';
import { WeekNavigator } from '../../features/operations/components/WeekNavigator';
import { WeeklyTimetable } from '../../features/operations/components/WeeklyTimetable';
import {
  useDeleteTimetableEntry,
  useTeacherWeekForAdmin,
  useTimetableEntries,
} from '../../features/operations/hooks';
import type { TeacherOption } from '../../types/academics';
import type { TimetableEntry } from '../../types/operations';
import { mondayOf, todayIso } from '../../utils/dates';

type Mode = 'class' | 'teacher';

function ClassTimetable() {
  const notify = useNotify();
  const years = useYearOptions();
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const [sectionId, setSectionId] = useState('');
  const [editing, setEditing] = useState<TimetableEntry | 'new' | null>(null);
  const [deleting, setDeleting] = useState<TimetableEntry | null>(null);

  const activeYear = years.data?.content.find((year) => year.status === 'ACTIVE');
  const yearId = yearChoice ?? activeYear?.id ?? '';
  const year = years.data?.content.find((candidate) => candidate.id === yearId);
  const sections = useSectionOptions(yearId || undefined);
  const section = sections.data?.content.find((candidate) => candidate.id === sectionId);
  const entries = useTimetableEntries({ page: 0, size: 100, sectionId }, Boolean(sectionId));
  const remove = useDeleteTimetableEntry();
  const closed = year?.status === 'CLOSED';

  return (
    <>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <TextField
          select
          size="small"
          label="Academic year"
          value={yearId}
          onChange={(event) => {
            setYearChoice(event.target.value);
            setSectionId('');
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
          label="Class"
          value={sectionId}
          onChange={(event) => setSectionId(event.target.value)}
          disabled={!yearId}
          sx={{ minWidth: 200 }}
        >
          {(sections.data?.content ?? []).map((candidate) => (
            <MenuItem key={candidate.id} value={candidate.id}>
              {candidate.gradeLevel.name} - {candidate.name}
            </MenuItem>
          ))}
        </TextField>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          disabled={!section || closed}
          onClick={() => setEditing('new')}
          sx={{ alignSelf: { sm: 'center' } }}
        >
          Add slot
        </Button>
      </Stack>

      {closed ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          {year?.name} is closed, so its timetable is read-only.
        </Alert>
      ) : null}
      {!sectionId ? (
        <Alert severity="info">Choose an academic year and a class to see its timetable.</Alert>
      ) : entries.isPending ? (
        <LoadingState />
      ) : entries.isError ? (
        <ErrorState error={entries.error} onRetry={() => void entries.refetch()} />
      ) : (
        <WeeklyTimetable
          entries={entries.data.content}
          detail={(entry) => entry.teacherName ?? 'No teacher'}
          emptyTitle="No timetable yet"
          emptyHint="Add slots for the subjects assigned to this class."
          onEdit={closed ? undefined : setEditing}
          onDelete={closed ? undefined : setDeleting}
        />
      )}

      {editing && section ? (
        <TimetableEntryDialog
          section={section}
          entry={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}
      <ConfirmDialog
        open={deleting !== null}
        title="Delete timetable slot?"
        message={`${deleting ? `${deleting.subjectName} on ${deleting.dayOfWeek.toLowerCase()}` : 'This slot'} will be removed from the timetable.`}
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
              notify.success('Slot deleted');
              setDeleting(null);
            },
          });
        }}
      />
    </>
  );
}

function TeacherTimetable() {
  const [teacher, setTeacher] = useState<TeacherOption | null>(null);
  const [weekStart, setWeekStart] = useState(() => mondayOf(todayIso()));
  const week = useTeacherWeekForAdmin(teacher?.id ?? '', weekStart);

  return (
    <>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <TeacherPicker value={teacher} onChange={setTeacher} />
        <WeekNavigator weekStart={weekStart} onChange={setWeekStart} />
      </Stack>
      {!teacher ? (
        <Alert severity="info">Choose a teacher to see their week.</Alert>
      ) : week.isPending ? (
        <LoadingState />
      ) : week.isError ? (
        <ErrorState error={week.error} onRetry={() => void week.refetch()} />
      ) : (
        <WeeklyTimetable
          entries={week.data.entries}
          detail={(entry) => entry.sectionName}
          emptyTitle="No classes this week"
          emptyHint="This teacher has nothing scheduled in the selected week."
        />
      )}
    </>
  );
}

export function TimetablePage() {
  const [mode, setMode] = useState<Mode>('class');

  return (
    <>
      <PageHeader
        title="Timetable"
        subtitle="Weekly schedule of each class and teacher. Clashes of class, teacher or room are refused."
      />
      <Tabs
        value={mode}
        onChange={(_event, value: Mode) => setMode(value)}
        aria-label="Timetable view"
        sx={{ mb: 2 }}
      >
        <Tab value="class" label="By class" />
        <Tab value="teacher" label="By teacher" />
      </Tabs>
      {mode === 'class' ? <ClassTimetable /> : <TeacherTimetable />}
    </>
  );
}
