import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { ExamStatusChip } from '../../features/exams/components/StatusChips';
import { useTeacherExams } from '../../features/exams/hooks';
import { formatDateRange, formatNumber } from '../../features/exams/labels';
import type { ExamTask, TeacherExam } from '../../types/exams';
import { formatDate } from '../../utils/format';

function TaskRow({ exam, task }: { exam: TeacherExam; task: ExamTask }) {
  const complete = task.expected > 0 && task.entered >= task.expected;
  const open = exam.status === 'OPEN_FOR_MARKS';
  const label = `${task.subjectName}, ${task.sectionName}`;
  return (
    <Stack
      component="li"
      direction={{ xs: 'column', sm: 'row' }}
      spacing={1.5}
      sx={{ py: 1.5, borderTop: 1, borderColor: 'divider', alignItems: { sm: 'center' } }}
    >
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
          {task.subjectName}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {task.sectionName} · out of {formatNumber(task.maxMarks)}, pass{' '}
          {formatNumber(task.passMarks)}
        </Typography>
      </Box>
      <Box sx={{ width: { sm: 200 } }}>
        <Typography variant="body2">
          {task.entered} of {task.expected} entered
        </Typography>
        <LinearProgress
          variant="determinate"
          value={task.expected === 0 ? 0 : Math.min(100, (task.entered / task.expected) * 100)}
          color={complete ? 'success' : 'primary'}
          aria-label={`Marks entered for ${label}`}
          sx={{ height: 8, borderRadius: 4, mt: 0.5 }}
        />
      </Box>
      <Button
        component={RouterLink}
        to={`/teacher/marks/${exam.id}/${task.examSubjectId}/${task.sectionId}`}
        variant={open ? 'contained' : 'outlined'}
        aria-label={`${open ? 'Enter marks' : 'View marks'}: ${label}`}
      >
        {open ? 'Enter marks' : 'View marks'}
      </Button>
    </Stack>
  );
}

export function TeacherMarksPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const exams = useTeacherExams({ page, size });

  return (
    <>
      <PageHeader
        title="Exams and marks"
        subtitle="Enter the marks of your classes while an exam is open for marks."
      />
      {exams.error && !exams.data ? (
        <ErrorState error={exams.error} onRetry={() => void exams.refetch()} />
      ) : exams.isPending ? (
        <LoadingState label="Loading exams" />
      ) : exams.data.content.length === 0 ? (
        <Paper variant="outlined">
          <EmptyState
            title="No exams to enter marks for"
            hint="Exams appear here once an administrator opens them for marks."
          />
        </Paper>
      ) : (
        <Stack spacing={2}>
          {exams.data.content.map((exam) => (
            <Paper
              key={exam.id}
              component="section"
              variant="outlined"
              sx={{ p: { xs: 1.5, sm: 2 } }}
              aria-labelledby={`exam-${exam.id}`}
            >
              <Stack
                direction="row"
                spacing={1}
                sx={{ justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}
              >
                <Box sx={{ minWidth: 0 }}>
                  <Typography
                    variant="h6"
                    component="h2"
                    id={`exam-${exam.id}`}
                    sx={{ overflowWrap: 'anywhere' }}
                  >
                    {exam.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {exam.academicYearName} · {exam.gradeLevelName} ·{' '}
                    {formatDateRange(exam.startDate, exam.endDate, formatDate)}
                  </Typography>
                </Box>
                <ExamStatusChip status={exam.status} />
              </Stack>
              {exam.tasks.length === 0 ? (
                <Typography color="text.secondary" sx={{ py: 1 }}>
                  You teach no subject of this exam.
                </Typography>
              ) : (
                <Box
                  component="ul"
                  aria-label={`Marks to enter for ${exam.name}`}
                  sx={{ listStyle: 'none', m: 0, p: 0 }}
                >
                  {exam.tasks.map((task) => (
                    <TaskRow
                      key={`${task.examSubjectId}-${task.sectionId}`}
                      exam={exam}
                      task={task}
                    />
                  ))}
                </Box>
              )}
              {exam.commentSections.length > 0 ? (
                <Stack
                  direction="row"
                  spacing={1}
                  useFlexGap
                  sx={{
                    pt: 1.5,
                    borderTop: 1,
                    borderColor: 'divider',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                  }}
                >
                  <Typography variant="body2" color="text.secondary">
                    Class teacher comments:
                  </Typography>
                  {exam.commentSections.map((section) => (
                    <Button
                      key={section.id}
                      component={RouterLink}
                      to={`/teacher/marks/${exam.id}/comments/${section.id}`}
                      variant="outlined"
                      size="small"
                      aria-label={`Comments for ${section.name}`}
                    >
                      {section.name}
                    </Button>
                  ))}
                </Stack>
              ) : null}
            </Paper>
          ))}
          <TablePagination
            component="div"
            count={exams.data.totalElements}
            page={exams.data.page}
            rowsPerPage={exams.data.size}
            rowsPerPageOptions={[5, 10, 20]}
            onPageChange={(_event, next) => setPage(next)}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            labelRowsPerPage="Rows"
            sx={{
              '.MuiTablePagination-toolbar': { flexWrap: 'wrap', justifyContent: 'center', px: 1 },
              '.MuiTablePagination-spacer': { display: { xs: 'none', sm: 'block' } },
            }}
          />
        </Stack>
      )}
    </>
  );
}
