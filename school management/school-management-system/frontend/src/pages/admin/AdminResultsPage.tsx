import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { ResponsiveRows, type RowColumn } from '../../features/exams/components/ResponsiveRows';
import { ResultChip } from '../../features/exams/components/StatusChips';
import { useExamResults, useExamSections, useExams } from '../../features/exams/hooks';
import { EXAM_STATUS_LABELS, formatNumber, formatPercent } from '../../features/exams/labels';
import type { StudentResult } from '../../types/exams';

export function AdminResultsPage() {
  const [params, setParams] = useSearchParams();
  const examId = params.get('examId') ?? '';
  const sectionId = params.get('sectionId') ?? '';

  const exams = useExams({ page: 0, size: 100, sort: 'createdAt', direction: 'desc' });
  const resultExams = (exams.data?.content ?? []).filter(
    (exam) => exam.status === 'LOCKED' || exam.status === 'PUBLISHED',
  );
  const selected = resultExams.find((exam) => exam.id === examId);
  const sections = useExamSections(selected?.academicYearId, selected?.gradeLevelId);
  const results = useExamResults(selected?.id, sectionId || undefined);

  const choose = (next: { examId?: string; sectionId?: string }) => {
    const query = new URLSearchParams();
    if (next.examId) query.set('examId', next.examId);
    if (next.sectionId) query.set('sectionId', next.sectionId);
    setParams(query, { replace: true });
  };

  const columns: RowColumn<StudentResult>[] = [
    {
      id: 'student',
      header: 'Student',
      render: (row) => (
        <>
          {row.studentName}
          <Typography variant="caption" color="text.secondary" component="div">
            {row.admissionNumber}
            {row.rollNumber === null ? '' : ` · Roll ${row.rollNumber}`}
          </Typography>
        </>
      ),
    },
    {
      id: 'total',
      header: 'Total',
      render: (row) => `${formatNumber(row.totalMarks)} / ${formatNumber(row.totalMax)}`,
    },
    { id: 'percentage', header: 'Percentage', render: (row) => formatPercent(row.percentage) },
    { id: 'grade', header: 'Grade', render: (row) => row.grade ?? '-' },
    { id: 'result', header: 'Result', render: (row) => <ResultChip result={row.result} /> },
  ];

  return (
    <>
      <PageHeader
        title="Results"
        subtitle="Look at the results of a locked or published exam, class by class."
      />
      {exams.error && !exams.data ? (
        <ErrorState error={exams.error} onRetry={() => void exams.refetch()} />
      ) : exams.isPending ? (
        <LoadingState label="Loading exams" />
      ) : resultExams.length === 0 ? (
        <Paper variant="outlined">
          <EmptyState
            title="No results yet"
            hint="Results are available once an exam has been locked."
          />
        </Paper>
      ) : (
        <>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <TextField
              select
              size="small"
              label="Exam"
              value={selected ? examId : ''}
              onChange={(event) => choose({ examId: event.target.value })}
              sx={{ minWidth: { sm: 320 }, flex: { sm: 1 }, maxWidth: { sm: 480 } }}
            >
              {resultExams.map((exam) => (
                <MenuItem key={exam.id} value={exam.id} sx={{ whiteSpace: 'normal' }}>
                  {exam.name} · {exam.gradeLevelName} · {exam.academicYearName} (
                  {EXAM_STATUS_LABELS[exam.status]})
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Class"
              value={sections.data?.content.some((s) => s.id === sectionId) ? sectionId : ''}
              disabled={!selected || sections.isPending}
              onChange={(event) => choose({ examId, sectionId: event.target.value })}
              sx={{ minWidth: { sm: 200 } }}
            >
              {(sections.data?.content ?? []).map((section) => (
                <MenuItem key={section.id} value={section.id}>
                  {section.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>

          {sections.error ? (
            <ErrorState
              title="Unable to load the classes"
              error={sections.error}
              onRetry={() => void sections.refetch()}
            />
          ) : null}
          {!selected || !sectionId ? (
            <Alert severity="info">Choose an exam and a class to see the results.</Alert>
          ) : results.isPending ? (
            <LoadingState label="Loading results" />
          ) : results.error ? (
            <ErrorState
              title="Unable to load the results"
              error={results.error}
              onRetry={() => void results.refetch()}
            />
          ) : (
            <Stack spacing={2}>
              {results.data.preview ? (
                <Alert severity="warning">
                  Preview - not published. These results can still change until you lock and publish
                  the exam, and students cannot see them.
                </Alert>
              ) : null}
              {results.data.results.length === 0 ? (
                <Paper variant="outlined">
                  <EmptyState title="No students in this class" />
                </Paper>
              ) : (
                <ResponsiveRows
                  label="Results"
                  rows={results.data.results}
                  columns={columns}
                  getRowId={(row) => row.studentId}
                  actions={(row) => (
                    <Button
                      component={RouterLink}
                      to={`/admin/results/${selected.id}/${row.studentId}`}
                      variant="outlined"
                      size="small"
                      aria-label={`Report card: ${row.studentName}`}
                    >
                      Report card
                    </Button>
                  )}
                />
              )}
            </Stack>
          )}
        </>
      )}
    </>
  );
}
