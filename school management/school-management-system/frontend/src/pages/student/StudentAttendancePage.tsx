import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useState } from 'react';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { StatusChip } from '../../components/StatusChip';
import { AttendanceStatusChip } from '../../features/operations/components/AttendanceStatusChip';
import { useMyAttendance } from '../../features/operations/hooks';
import { formatPercentage, TYPE_LABELS } from '../../features/operations/labels';
import type { Rate } from '../../types/operations';
import { currentMonth, shiftMonth } from '../../utils/dates';
import { formatDate } from '../../utils/format';

function RateCard({ title, rate, threshold }: { title: string; rate: Rate; threshold: number }) {
  const below = rate.percentage !== null && rate.percentage < threshold;
  return (
    <Card variant="outlined" sx={{ flex: 1 }}>
      <CardContent>
        <Typography variant="subtitle2" color="text.secondary">
          {title}
        </Typography>
        <Typography variant="h4" component="p" sx={{ my: 0.5 }}>
          {formatPercentage(rate.percentage)}
        </Typography>
        {below ? <StatusChip label={`Below ${threshold}%`} tone="warning" /> : null}
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Present {rate.present} · Late {rate.late} · Half day {rate.halfDay} · Absent {rate.absent}{' '}
          · Excused {rate.excused}
        </Typography>
      </CardContent>
    </Card>
  );
}

export function StudentAttendancePage() {
  const [month, setMonth] = useState(currentMonth());
  const attendance = useMyAttendance(month);

  return (
    <>
      <PageHeader
        title="My attendance"
        subtitle="Finalized attendance for the month. Excused days are not counted against you."
      />
      <Stack direction="row" spacing={1} sx={{ mb: 2, alignItems: 'center' }}>
        <IconButton aria-label="Previous month" onClick={() => setMonth(shiftMonth(month, -1))}>
          <ChevronLeftIcon />
        </IconButton>
        <TextField
          size="small"
          type="month"
          label="Month"
          value={month}
          onChange={(event) => event.target.value && setMonth(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <IconButton
          aria-label="Next month"
          disabled={month >= currentMonth()}
          onClick={() => setMonth(shiftMonth(month, 1))}
        >
          <ChevronRightIcon />
        </IconButton>
      </Stack>

      {attendance.isPending ? <LoadingState /> : null}
      {attendance.isError ? (
        <ErrorState error={attendance.error} onRetry={() => void attendance.refetch()} />
      ) : null}
      {attendance.data ? (
        <Stack spacing={3}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <RateCard
              title="Daily attendance"
              rate={attendance.data.daily}
              threshold={attendance.data.threshold}
            />
            <RateCard
              title="Subject-wise attendance"
              rate={attendance.data.subjectWise}
              threshold={attendance.data.threshold}
            />
          </Stack>
          {attendance.data.days.length === 0 ? (
            <EmptyState
              title="No attendance recorded"
              hint="Nothing has been finalized for this month yet."
            />
          ) : (
            <Box>
              <Typography variant="h6" component="h2" gutterBottom>
                Day by day
              </Typography>
              <Stack
                component="ul"
                spacing={1}
                aria-label="Attendance by day"
                sx={{ m: 0, p: 0, listStyle: 'none' }}
              >
                {attendance.data.days.map((day, index) => (
                  <Card
                    key={`${day.date}-${day.subjectName ?? 'daily'}-${day.periodNumber ?? index}`}
                    component="li"
                    variant="outlined"
                  >
                    <CardContent
                      sx={{
                        py: 1.5,
                        '&:last-child': { pb: 1.5 },
                        display: 'flex',
                        justifyContent: 'space-between',
                        gap: 1,
                        alignItems: 'center',
                      }}
                    >
                      <Box sx={{ minWidth: 0 }}>
                        <Typography>{formatDate(day.date)}</Typography>
                        <Typography
                          variant="body2"
                          color="text.secondary"
                          sx={{ overflowWrap: 'anywhere' }}
                        >
                          {day.subjectName ?? TYPE_LABELS[day.type]}
                          {day.periodNumber ? ` · period ${day.periodNumber}` : ''}
                          {day.remarks ? ` · ${day.remarks}` : ''}
                        </Typography>
                      </Box>
                      <AttendanceStatusChip status={day.status} />
                    </CardContent>
                  </Card>
                ))}
              </Stack>
            </Box>
          )}
          <Alert severity="info">Attendance appears here after your teacher finalizes it.</Alert>
        </Stack>
      ) : null}
    </>
  );
}
