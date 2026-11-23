import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';
import { StatusChip } from '../../components/StatusChip';
import {
  BigCount,
  Hint,
  SlotList,
  Stats,
  Widget,
} from '../../features/dashboard/components/Widgets';
import { useStudentDashboard } from '../../features/dashboard/hooks';
import { formatPercent } from '../../features/exams/labels';
import { formatMoney } from '../../features/fees/labels';
import { formatDate, formatDateTime } from '../../utils/format';
import { DashboardOverview } from '../DashboardOverview';

export function StudentDashboardPage() {
  const { data, error, isPending, refetch } = useStudentDashboard();
  const result = data?.latestResult;

  return (
    <DashboardOverview
      title="Student dashboard"
      subtitle="Your timetable, assignments and results."
      isPending={isPending}
      error={error}
      onRetry={() => void refetch()}
    >
      {data ? (
        <>
          <Widget title="My class">
            {data.className ? (
              <Typography variant="h5" component="p" sx={{ overflowWrap: 'anywhere' }}>
                {data.className}
              </Typography>
            ) : (
              <Hint>You are not enrolled in a class yet</Hint>
            )}
          </Widget>

          <Widget title="Today's timetable" wide>
            <SlotList slots={data.todaySlots} label="Today's periods" />
          </Widget>

          <Widget
            title="Attendance this month"
            link={{ to: '/student/attendance', label: 'View attendance' }}
          >
            {data.attendancePercent === null ? (
              <Hint>No register finalized yet</Hint>
            ) : (
              <BigCount
                value={data.attendancePercent}
                caption="present this month"
                format={formatPercent}
              />
            )}
          </Widget>

          <Widget
            title="Upcoming work"
            wide
            link={{ to: '/student/assignments', label: 'All assignments' }}
          >
            {data.upcomingWork.length === 0 ? (
              <Hint>No upcoming work</Hint>
            ) : (
              <Stack
                component="ul"
                aria-label="Upcoming assignments"
                spacing={1.5}
                sx={{ listStyle: 'none', m: 0, p: 0 }}
              >
                {data.upcomingWork.map((work) => (
                  <Box component="li" key={work.assignmentId} sx={{ minWidth: 0 }}>
                    <Link
                      component={RouterLink}
                      to={`/student/assignments/${work.assignmentId}`}
                      sx={{
                        overflowWrap: 'anywhere',
                        display: 'inline-flex',
                        minHeight: 44,
                        alignItems: 'center',
                      }}
                    >
                      {work.title}
                    </Link>
                    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ overflowWrap: 'anywhere' }}
                      >
                        {`${work.subjectName} \u00b7 Due ${formatDateTime(work.dueAt)}`}
                      </Typography>
                      {work.resubmission ? (
                        <StatusChip label="Resubmission requested" tone="warning" />
                      ) : null}
                    </Stack>
                  </Box>
                ))}
              </Stack>
            )}
          </Widget>

          <Widget title="Latest result">
            {result ? (
              <Stack spacing={0.5}>
                <Link
                  component={RouterLink}
                  to={`/student/results/${result.examId}`}
                  sx={{
                    overflowWrap: 'anywhere',
                    display: 'inline-flex',
                    minHeight: 44,
                    alignItems: 'center',
                  }}
                >
                  {result.examName}
                </Link>
                <Typography variant="h5" component="p">
                  {formatPercent(result.percentage)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {[result.grade ? `Grade ${result.grade}` : null, result.result]
                    .filter(Boolean)
                    .join(' \u00b7 ')}
                </Typography>
              </Stack>
            ) : (
              <Hint>No result published yet</Hint>
            )}
          </Widget>

          <Widget title="Fees" link={{ to: '/student/fees', label: 'View fees' }}>
            <Stats
              label="Fees"
              items={[
                { label: 'Outstanding', value: formatMoney(data.fees.outstanding) },
                { label: 'Overdue fees', value: data.fees.overdueFees },
              ]}
            />
            <Hint>
              {data.fees.nextDueDate
                ? `Next due ${formatDate(data.fees.nextDueDate)}`
                : 'No upcoming due date'}
            </Hint>
          </Widget>

          <Widget
            title="Unread announcements"
            link={{ to: '/student/announcements', label: 'Open announcements' }}
          >
            <BigCount
              value={data.unreadAnnouncements}
              caption={
                data.unreadAnnouncements === 1 ? 'unread announcement' : 'unread announcements'
              }
            />
          </Widget>
        </>
      ) : null}
    </DashboardOverview>
  );
}
