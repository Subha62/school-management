import Box from '@mui/material/Box';
import { useState } from 'react';
import { ErrorState } from '../../components/ErrorState';
import { LoadingState } from '../../components/LoadingState';
import { PageHeader } from '../../components/PageHeader';
import { WeekNavigator } from '../../features/operations/components/WeekNavigator';
import { WeeklyTimetable } from '../../features/operations/components/WeeklyTimetable';
import { useMyStudentWeek } from '../../features/operations/hooks';
import { mondayOf, todayIso } from '../../utils/dates';

export function StudentTimetablePage() {
  const [weekStart, setWeekStart] = useState(() => mondayOf(todayIso()));
  const week = useMyStudentWeek(weekStart);

  return (
    <>
      <PageHeader title="My timetable" subtitle="Your classes this week." />
      <WeekNavigator weekStart={weekStart} onChange={setWeekStart} />
      <Box sx={{ mt: 2 }}>
        {week.isPending ? (
          <LoadingState />
        ) : week.isError ? (
          <ErrorState error={week.error} onRetry={() => void week.refetch()} />
        ) : (
          <WeeklyTimetable
            entries={week.data.entries}
            detail={(entry) => entry.teacherName ?? 'No teacher'}
            emptyTitle="No classes this week"
            emptyHint="Nothing is scheduled for your class in this week, or you are not enrolled in a class yet."
          />
        )}
      </Box>
    </>
  );
}
