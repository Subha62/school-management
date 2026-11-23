import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Pagination from '@mui/material/Pagination';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useNotify } from '../../app/notifications';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataList, type Column } from '../../components/DataList';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { useGradeOptions, useSectionOptions, useYearOptions } from '../../features/academics/hooks';
import { useEnrollments, usePromoteStudents, usePromotions } from '../../features/people/hooks';
import type { Enrollment, PromotionBatch, PromotionSummary } from '../../types/people';

const PAGE_SIZE = 50;

function formatWhen(iso: string): string {
  return new Date(iso).toLocaleString();
}

export function PromotionPage() {
  const notify = useNotify();
  const years = useYearOptions();
  const grades = useGradeOptions();
  const promote = usePromoteStudents();

  const [sourceChoice, setSourceChoice] = useState<string | null>(null);
  const [targetChoice, setTargetChoice] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [overrides, setOverrides] = useState<Readonly<Record<string, string>>>({});
  const [confirming, setConfirming] = useState(false);
  const [summary, setSummary] = useState<PromotionSummary | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [historySize, setHistorySize] = useState(10);

  const yearList = years.data?.content ?? [];
  const sourceId = sourceChoice ?? yearList.find((year) => year.status === 'ACTIVE')?.id ?? '';
  const source = yearList.find((year) => year.id === sourceId);
  // Default target: the earliest open year that starts after the source year.
  const suggestedTarget = yearList
    .filter(
      (year) =>
        year.status !== 'CLOSED' && source !== undefined && year.startDate > source.startDate,
    )
    .sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
  const targetId = targetChoice ?? suggestedTarget?.id ?? '';
  const target = yearList.find((year) => year.id === targetId);

  const enrollments = useEnrollments(
    { page, size: PAGE_SIZE, academicYearId: sourceId, status: 'ACTIVE' },
    Boolean(sourceId),
  );
  const targetSections = useSectionOptions(targetId || undefined);
  const history = usePromotions({ page: historyPage, size: historySize });

  const gradeOrder = useMemo(
    () => new Map((grades.data?.content ?? []).map((grade) => [grade.id, grade.displayOrder])),
    [grades.data],
  );
  const openSections = useMemo(
    () => (targetSections.data?.content ?? []).filter((section) => section.active),
    [targetSections.data],
  );

  /** The same section letter in the next grade up, or any section of that grade. */
  const suggest = (enrollment: Enrollment): string => {
    const order = gradeOrder.get(enrollment.gradeLevel.id);
    if (order === undefined) return '';
    const nextOrder = Math.min(
      ...openSections
        .map((section) => gradeOrder.get(section.gradeLevel.id) ?? Number.POSITIVE_INFINITY)
        .filter((candidate) => candidate > order),
    );
    if (!Number.isFinite(nextOrder)) return '';
    const inNextGrade = openSections.filter(
      (section) => gradeOrder.get(section.gradeLevel.id) === nextOrder,
    );
    return (
      inNextGrade.find((section) => section.name === enrollment.section.name)?.id ??
      inNextGrade[0]?.id ??
      ''
    );
  };
  const targetFor = (enrollment: Enrollment) =>
    overrides[enrollment.student.id] ?? suggest(enrollment);

  const rows = enrollments.data?.content ?? [];
  const chosen = rows.filter((row) => selected.has(row.student.id));
  const missingTarget = chosen.filter((row) => !targetFor(row));
  const datesOk = Boolean(source && target && source.id !== target.id);

  const resetSelection = () => {
    setSelected(new Set());
    setOverrides({});
  };

  const toggle = (studentId: string, on: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      if (on) next.add(studentId);
      else next.delete(studentId);
      return next;
    });

  const allOnPage = rows.length > 0 && rows.every((row) => selected.has(row.student.id));

  const historyColumns: Column<PromotionBatch>[] = [
    { id: 'when', header: 'Date', render: (b) => formatWhen(b.performedAt) },
    {
      id: 'route',
      header: 'From / to',
      render: (b) => `${b.sourceAcademicYear} to ${b.targetAcademicYear}`,
    },
    { id: 'by', header: 'By', hideBelow: 'md', render: (b) => b.performedBy },
    { id: 'promoted', header: 'Promoted', render: (b) => b.promoted },
    { id: 'failed', header: 'Not promoted', render: (b) => b.failed },
  ];

  return (
    <>
      <PageHeader
        title="Student promotion"
        subtitle="Move students from one academic year to the next. Every student is checked on their own, and the old enrollment is kept as history."
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} useFlexGap>
        <TextField
          select
          size="small"
          label="From academic year"
          value={sourceId}
          onChange={(event) => {
            setSourceChoice(event.target.value);
            setTargetChoice(null);
            setPage(0);
            resetSelection();
          }}
          sx={{ minWidth: 200 }}
        >
          {yearList.map((year) => (
            <MenuItem key={year.id} value={year.id}>
              {year.name}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label="To academic year"
          value={targetId}
          onChange={(event) => {
            setTargetChoice(event.target.value);
            setOverrides({});
          }}
          sx={{ minWidth: 200 }}
        >
          {yearList
            .filter((year) => year.status !== 'CLOSED' && year.id !== sourceId)
            .map((year) => (
              <MenuItem key={year.id} value={year.id}>
                {year.name}
              </MenuItem>
            ))}
        </TextField>
      </Stack>

      {years.isPending || (Boolean(sourceId) && enrollments.isPending) ? <LoadingState /> : null}
      {enrollments.isError ? (
        <ErrorState error={enrollments.error} onRetry={() => void enrollments.refetch()} />
      ) : null}
      {!years.isPending && !target ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Create or plan a later academic year first. Students are promoted into an open year that
          starts after the year they are leaving.
        </Alert>
      ) : null}

      {enrollments.data && target ? (
        rows.length === 0 ? (
          <EmptyState
            title="No students to promote"
            hint={`There are no active enrollments in ${source?.name ?? 'this year'}.`}
          />
        ) : (
          <Paper variant="outlined" sx={{ mb: 3 }}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1}
              sx={{ p: 2, justifyContent: 'space-between', alignItems: { sm: 'center' } }}
            >
              <FormControlLabel
                label={`Select all on this page (${rows.length})`}
                control={
                  <Checkbox
                    checked={allOnPage}
                    onChange={(event) =>
                      setSelected((current) => {
                        const next = new Set(current);
                        rows.forEach((row) =>
                          event.target.checked
                            ? next.add(row.student.id)
                            : next.delete(row.student.id),
                        );
                        return next;
                      })
                    }
                  />
                }
              />
              <Button
                variant="contained"
                disabled={chosen.length === 0 || missingTarget.length > 0 || !datesOk}
                onClick={() => setConfirming(true)}
              >
                {`Promote ${chosen.length} selected`}
              </Button>
            </Stack>
            {missingTarget.length > 0 ? (
              <Alert severity="warning" sx={{ mx: 2, mb: 2 }}>
                Choose a target section for every selected student ({missingTarget.length} missing).
              </Alert>
            ) : null}
            <Box
              component="ul"
              aria-label="Students to promote"
              sx={{ listStyle: 'none', m: 0, p: 0 }}
            >
              {rows.map((row) => (
                <Stack
                  component="li"
                  key={row.id}
                  direction={{ xs: 'column', md: 'row' }}
                  spacing={1.5}
                  sx={{
                    px: 2,
                    py: 1.5,
                    borderTop: 1,
                    borderColor: 'divider',
                    alignItems: { md: 'center' },
                  }}
                >
                  <FormControlLabel
                    sx={{ flex: 1, minWidth: 0, m: 0 }}
                    label={
                      <Box sx={{ minWidth: 0 }}>
                        <Typography sx={{ overflowWrap: 'anywhere' }}>
                          {row.student.name} ({row.student.admissionNumber})
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {row.gradeLevel.name} - {row.section.name}
                        </Typography>
                      </Box>
                    }
                    control={
                      <Checkbox
                        checked={selected.has(row.student.id)}
                        onChange={(event) => toggle(row.student.id, event.target.checked)}
                        slotProps={{ input: { 'aria-label': `Select ${row.student.name}` } }}
                      />
                    }
                  />
                  <TextField
                    select
                    size="small"
                    label="Target section"
                    value={targetFor(row)}
                    onChange={(event) =>
                      setOverrides((current) => ({
                        ...current,
                        [row.student.id]: event.target.value,
                      }))
                    }
                    slotProps={{
                      htmlInput: { 'aria-label': `Target section for ${row.student.name}` },
                    }}
                    sx={{ minWidth: { md: 220 } }}
                  >
                    {openSections.map((section) => (
                      <MenuItem key={section.id} value={section.id}>
                        {section.gradeLevel.name} - {section.name}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
              ))}
            </Box>
            {enrollments.data.totalPages > 1 ? (
              <Stack sx={{ p: 2, alignItems: 'center' }}>
                <Pagination
                  count={enrollments.data.totalPages}
                  page={page + 1}
                  onChange={(_event, value) => setPage(value - 1)}
                />
              </Stack>
            ) : null}
          </Paper>
        )
      ) : null}

      {summary ? (
        <Alert
          severity={summary.failed === 0 ? 'success' : summary.promoted === 0 ? 'error' : 'warning'}
          sx={{ mb: 3 }}
          onClose={() => setSummary(null)}
        >
          <AlertTitle>
            {summary.promoted} of {summary.total} students promoted to {summary.targetAcademicYear}
          </AlertTitle>
          {summary.failed > 0 ? (
            <Box component="ul" aria-label="Students not promoted" sx={{ m: 0, pl: 2.5 }}>
              {summary.results
                .filter((result) => result.outcome === 'FAILED')
                .map((result) => (
                  <li key={result.studentId}>
                    {result.studentName ?? 'Unknown student'}
                    {result.admissionNumber ? ` (${result.admissionNumber})` : ''}: {result.reason}
                  </li>
                ))}
            </Box>
          ) : null}
        </Alert>
      ) : null}

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
        Promotion history
      </Typography>
      <DataList
        data={history.data}
        columns={historyColumns}
        getRowId={(batch) => batch.id}
        getRowLabel={(batch) => `${batch.sourceAcademicYear} to ${batch.targetAcademicYear}`}
        isLoading={history.isPending}
        error={history.error}
        onRetry={() => void history.refetch()}
        emptyTitle="No promotions yet"
        emptyHint="Promotion runs are listed here with who ran them and the result."
        onPageChange={setHistoryPage}
        onPageSizeChange={(newSize) => {
          setHistorySize(newSize);
          setHistoryPage(0);
        }}
      />

      <ConfirmDialog
        open={confirming}
        title="Promote selected students?"
        message={`${chosen.length} students will move from ${source?.name ?? ''} to ${target?.name ?? ''}. Students who cannot be promoted are skipped and reported.`}
        confirmLabel="Promote"
        loading={promote.isPending}
        error={promote.error}
        onCancel={() => {
          promote.reset();
          setConfirming(false);
        }}
        onConfirm={() =>
          promote.mutate(
            {
              sourceAcademicYearId: sourceId,
              targetAcademicYearId: targetId,
              items: chosen.map((row) => ({
                studentId: row.student.id,
                targetSectionId: targetFor(row),
              })),
            },
            {
              onSuccess: (result) => {
                setConfirming(false);
                setSummary(result);
                resetSelection();
                notify.success(`${result.promoted} of ${result.total} students promoted`);
              },
            },
          )
        }
      />
    </>
  );
}
