import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { BigCount, Hint, SlotList, Widget } from '../../features/dashboard/components/Widgets';
import { useTeacherDashboard } from '../../features/dashboard/hooks';
import { DashboardOverview } from '../DashboardOverview';

const plural = (count: number, one: string, many: string) => (count === 1 ? one : many);

export function TeacherDashboardPage() {
  const { data, error, isPending, refetch } = useTeacherDashboard();

  return (
    <DashboardOverview
      title="Teacher dashboard"
      subtitle="Your classes, attendance and assignments."
      isPending={isPending}
      error={error}
      onRetry={() => void refetch()}
    >
      {data ? (
        <>
          <Widget title="Today's timetable" wide>
            <SlotList slots={data.todaySlots} label="Today's periods" />
          </Widget>

          <Widget title="Classes taught">
            <BigCount
              value={data.classesTaught}
              caption={plural(data.classesTaught, 'class this year', 'classes this year')}
            />
          </Widget>

          <Widget
            title="Submissions awaiting review"
            link={{ to: '/teacher/submissions', label: 'Review submissions' }}
          >
            <BigCount
              value={data.awaitingReview}
              caption={plural(data.awaitingReview, 'submission to review', 'submissions to review')}
            />
          </Widget>

          <Widget
            title="Marks still to enter"
            link={{ to: '/teacher/marks', label: 'Enter marks' }}
          >
            <BigCount
              value={data.marksMissing}
              caption={plural(data.marksMissing, 'mark missing', 'marks missing')}
            />
          </Widget>

          <Widget
            title="Registers to take today"
            link={{ to: '/teacher/attendance', label: 'Take attendance' }}
          >
            {data.registersToTake.length === 0 ? (
              <Hint>No daily registers are waiting</Hint>
            ) : (
              <Stack
                component="ul"
                aria-label="Classes without a register"
                spacing={0.5}
                sx={{ listStyle: 'none', m: 0, p: 0 }}
              >
                {data.registersToTake.map((name) => (
                  <Box component="li" key={name} sx={{ minWidth: 0 }}>
                    <Typography sx={{ overflowWrap: 'anywhere' }}>{name}</Typography>
                  </Box>
                ))}
              </Stack>
            )}
          </Widget>

          <Widget
            title="Unread announcements"
            link={{ to: '/teacher/announcements', label: 'Open announcements' }}
          >
            <BigCount
              value={data.unreadAnnouncements}
              caption={plural(
                data.unreadAnnouncements,
                'unread announcement',
                'unread announcements',
              )}
            />
          </Widget>
        </>
      ) : null}
    </DashboardOverview>
  );
}
