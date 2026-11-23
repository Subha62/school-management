import { BigCount, Hint, Stats, Widget } from '../../features/dashboard/components/Widgets';
import { RecentActivity } from '../../features/dashboard/components/RecentActivity';
import { useAdminDashboard } from '../../features/dashboard/hooks';
import { formatMoney } from '../../features/fees/labels';
import { DashboardOverview } from '../DashboardOverview';

export function AdminDashboardPage() {
  const { data, error, isPending, refetch } = useAdminDashboard();
  const attendance = data?.attendanceToday;
  const fees = data?.fees;
  const exams = data?.exams;

  return (
    <DashboardOverview
      title="Admin dashboard"
      subtitle="School-wide overview and administration."
      isPending={isPending}
      error={error}
      onRetry={() => void refetch()}
    >
      {data && attendance && exams ? (
        <>
          <Widget title="People and classes">
            <Stats
              label="People and classes"
              items={[
                { label: 'Active students', value: data.activeStudents },
                { label: 'Active teachers', value: data.activeTeachers },
                { label: 'Active classes', value: data.activeClasses },
              ]}
            />
            <Hint>
              {data.academicYearName
                ? `Academic year ${data.academicYearName}`
                : 'No active academic year'}
            </Hint>
          </Widget>

          <Widget
            title="Today's attendance"
            link={{ to: '/admin/attendance-reports', label: 'View attendance reports' }}
          >
            {attendance.registersFinalized + attendance.registersOpen === 0 ? (
              <Hint>No registers have been opened today</Hint>
            ) : (
              <Stats
                label="Today's registers"
                items={[
                  { label: 'Registers finalized', value: attendance.registersFinalized },
                  { label: 'Registers open', value: attendance.registersOpen },
                ]}
              />
            )}
          </Widget>

          <Widget
            title="Fee snapshot"
            link={fees ? { to: '/admin/student-fees', label: 'View student fees' } : undefined}
          >
            {fees ? (
              <>
                <Stats
                  label="Fee snapshot"
                  items={[
                    { label: 'Billed', value: formatMoney(fees.billed) },
                    { label: 'Collected', value: formatMoney(fees.collected) },
                    { label: 'Outstanding', value: formatMoney(fees.outstanding) },
                    { label: 'Overdue', value: formatMoney(fees.overdue) },
                  ]}
                />
                <Hint>
                  {fees.overdueFees === 1
                    ? '1 fee is overdue'
                    : `${fees.overdueFees} fees are overdue`}
                </Hint>
              </>
            ) : (
              <Hint>Fee figures appear once an academic year is active.</Hint>
            )}
          </Widget>

          <Widget title="Exams in progress" link={{ to: '/admin/exams', label: 'View exams' }}>
            <Stats
              label="Exams"
              items={[
                { label: 'Open for marks', value: exams.openForMarks },
                { label: 'Awaiting publication', value: exams.awaitingPublication },
                { label: 'Published', value: exams.published },
              ]}
            />
          </Widget>

          <Widget
            title="Live announcements"
            link={{ to: '/admin/announcements', label: 'Manage announcements' }}
          >
            <BigCount
              value={data.liveAnnouncements}
              caption={
                data.liveAnnouncements === 1 ? 'announcement live now' : 'announcements live now'
              }
            />
          </Widget>

          <Widget
            title="Recent activity"
            wide
            link={{ to: '/admin/audit-logs', label: 'View all' }}
          >
            <RecentActivity entries={data.recentActivity} />
          </Widget>
        </>
      ) : null}
    </DashboardOverview>
  );
}
