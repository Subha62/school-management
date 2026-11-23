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
import { StatusChip } from '../../components/StatusChip';
import { useGradeOptions, useSectionOptions, useYearOptions } from '../../features/academics/hooks';
import { EnrollDialog } from '../../features/people/components/EnrollDialog';
import { TransferDialog } from '../../features/people/components/TransferDialog';
import {
  useCompleteEnrollment,
  useEnrollments,
  useWithdrawEnrollment,
} from '../../features/people/hooks';
import { ENROLLMENT_LABELS, enrollmentTone, toOptions } from '../../features/people/labels';
import type { Enrollment, EnrollmentStatus } from '../../types/people';
import { formatDate } from '../../utils/format';

const ALL = 'all';

type Pending = { kind: 'withdraw' | 'complete'; enrollment: Enrollment };

export function EnrollmentsPage() {
  const notify = useNotify();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [search, setSearch] = useState('');
  const [yearChoice, setYearChoice] = useState<string | null>(null);
  const [gradeId, setGradeId] = useState(ALL);
  const [sectionId, setSectionId] = useState(ALL);
  const [status, setStatus] = useState<string>('ACTIVE');
  const [enrolling, setEnrolling] = useState(false);
  const [transferring, setTransferring] = useState<Enrollment | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);

  const years = useYearOptions();
  const grades = useGradeOptions();
  const activeYear = years.data?.content.find((year) => year.status === 'ACTIVE');
  // Until the user chooses, show the current (active) year, which is what administrators work on.
  const yearId = yearChoice ?? activeYear?.id ?? ALL;
  const sections = useSectionOptions(yearId === ALL ? undefined : yearId);

  const enrollments = useEnrollments(
    {
      page,
      size,
      search: search || undefined,
      academicYearId: yearId === ALL ? undefined : yearId,
      gradeLevelId: gradeId === ALL ? undefined : gradeId,
      sectionId: sectionId === ALL ? undefined : sectionId,
      status: status === ALL ? undefined : (status as EnrollmentStatus),
    },
    !years.isPending,
  );
  const withdraw = useWithdrawEnrollment();
  const complete = useCompleteEnrollment();
  const finishing = pending?.kind === 'complete' ? complete : withdraw;

  const onSearch = useCallback((value: string) => {
    setSearch(value);
    setPage(0);
  }, []);

  const columns: Column<Enrollment>[] = [
    { id: 'roll', header: 'Roll', hideBelow: 'sm', render: (e) => e.rollNumber ?? '-' },
    {
      id: 'student',
      header: 'Student',
      render: (e) => `${e.student.name} (${e.student.admissionNumber})`,
    },
    {
      id: 'class',
      header: 'Class',
      render: (e) => `${e.gradeLevel.name} - ${e.section.name}`,
    },
    { id: 'year', header: 'Academic year', hideBelow: 'md', render: (e) => e.academicYear.name },
    {
      id: 'date',
      header: 'Enrolled',
      hideBelow: 'lg',
      render: (e) => formatDate(e.enrollmentDate),
    },
    {
      id: 'status',
      header: 'Status',
      render: (e) => (
        <StatusChip label={ENROLLMENT_LABELS[e.status]} tone={enrollmentTone(e.status)} />
      ),
    },
  ];

  const inactive = (enrollment: Enrollment) => enrollment.status !== 'ACTIVE';
  const actions: RowAction<Enrollment>[] = [
    { label: 'Transfer', onSelect: setTransferring, hidden: inactive },
    {
      label: 'Mark completed',
      onSelect: (enrollment) => setPending({ kind: 'complete', enrollment }),
      hidden: inactive,
    },
    {
      label: 'Withdraw',
      onSelect: (enrollment) => setPending({ kind: 'withdraw', enrollment }),
      destructive: true,
      hidden: inactive,
    },
  ];

  return (
    <>
      <PageHeader
        title="Enrollments"
        subtitle="Which students are in which section each year. Past enrollments are kept as history."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEnrolling(true)}>
            Enroll student
          </Button>
        }
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap' }}
        useFlexGap
      >
        <SearchField label="Search by student" onSearch={onSearch} />
        <TextField
          select
          size="small"
          label="Academic year"
          value={yearId}
          onChange={(event) => {
            setYearChoice(event.target.value);
            setSectionId(ALL);
            setPage(0);
          }}
          sx={{ minWidth: 170 }}
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
          sx={{ minWidth: 150 }}
        >
          <MenuItem value={ALL}>All grades</MenuItem>
          {(grades.data?.content ?? []).map((grade) => (
            <MenuItem key={grade.id} value={grade.id}>
              {grade.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="Section"
          value={sectionId}
          disabled={yearId === ALL}
          onChange={(event) => {
            setSectionId(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 150 }}
        >
          <MenuItem value={ALL}>All sections</MenuItem>
          {(sections.data?.content ?? []).map((section) => (
            <MenuItem key={section.id} value={section.id}>
              {section.gradeLevel.name} - {section.name}
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
          sx={{ minWidth: 150 }}
        >
          <MenuItem value={ALL}>All statuses</MenuItem>
          {toOptions<EnrollmentStatus>(ENROLLMENT_LABELS).map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      <DataList
        data={enrollments.data}
        columns={columns}
        actions={actions}
        getRowId={(enrollment) => enrollment.id}
        getRowLabel={(enrollment) => enrollment.student.name}
        isLoading={enrollments.isPending || years.isPending}
        error={enrollments.error}
        onRetry={() => void enrollments.refetch()}
        emptyTitle="No enrollments found"
        emptyHint="Enroll students in a section, or change the filters."
        onPageChange={setPage}
        onPageSizeChange={(newSize) => {
          setSize(newSize);
          setPage(0);
        }}
      />

      {enrolling ? (
        <EnrollDialog
          defaultYearId={yearId === ALL ? undefined : yearId}
          onClose={() => setEnrolling(false)}
        />
      ) : null}
      {transferring ? (
        <TransferDialog enrollment={transferring} onClose={() => setTransferring(null)} />
      ) : null}
      <ConfirmDialog
        open={pending !== null}
        title={pending?.kind === 'complete' ? 'Mark enrollment completed?' : 'Withdraw student?'}
        message={
          pending
            ? pending.kind === 'complete'
              ? `${pending.enrollment.student.name}'s enrollment in ${pending.enrollment.gradeLevel.name} - ${pending.enrollment.section.name} will be closed as completed.`
              : `${pending.enrollment.student.name} will be removed from ${pending.enrollment.gradeLevel.name} - ${pending.enrollment.section.name}. The enrollment stays in the student's history.`
            : ''
        }
        confirmLabel={pending?.kind === 'complete' ? 'Mark completed' : 'Withdraw'}
        destructive={pending?.kind === 'withdraw'}
        loading={finishing.isPending}
        error={finishing.error}
        onCancel={() => {
          finishing.reset();
          setPending(null);
        }}
        onConfirm={() => {
          if (!pending) return;
          finishing.mutate(pending.enrollment.id, {
            onSuccess: () => {
              notify.success(
                pending.kind === 'complete' ? 'Enrollment completed' : 'Student withdrawn',
              );
              setPending(null);
            },
          });
        }}
      />
    </>
  );
}
