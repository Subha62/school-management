import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../../api/errors';
import {
  makeAdminDashboard,
  makeAuditEntry,
  makeStudentDashboard,
  makeTeacherDashboard,
  pageOf,
} from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import { StudentDashboardPage } from '../student/StudentDashboardPage';
import { TeacherDashboardPage } from '../teacher/TeacherDashboardPage';
import { AdminDashboardPage } from './AdminDashboardPage';
import { AuditLogsPage } from './AuditLogsPage';

vi.mock('../../hooks/useSystemInfo', () => ({
  useSystemInfo: () => ({
    data: { name: 'school-management-system', status: 'UP', profile: 'dev' },
    error: null,
    isPending: false,
    refetch: vi.fn(),
  }),
}));

const dashboard = vi.hoisted(() => ({
  getAdminDashboard: vi.fn(),
  getTeacherDashboard: vi.fn(),
  getStudentDashboard: vi.fn(),
}));
const audit = vi.hoisted(() => ({
  listAuditLogs: vi.fn(),
  listAuditActions: vi.fn(),
  listAuditEntityTypes: vi.fn(),
  getAuditEntry: vi.fn(),
}));
vi.mock('../../features/dashboard/api', () => dashboard);
vi.mock('../../features/audit/api', () => audit);

const asAdmin = (ui: React.ReactElement, route = '/admin') =>
  renderWithProviders(ui, { route, auth: { user: makeUser({ roles: ['ROLE_ADMIN'] }) } });

const region = async (name: string) => within(await screen.findByRole('region', { name }));

beforeEach(() => {
  setViewportWidth(1440);
  [...Object.values(dashboard), ...Object.values(audit)].forEach((mock) => mock.mockReset());
  audit.listAuditLogs.mockResolvedValue(pageOf([]));
  audit.listAuditActions.mockResolvedValue(['PAYMENT_REVERSED', 'LOGIN_SUCCESS']);
  audit.listAuditEntityTypes.mockResolvedValue(['Payment', 'StudentFee']);
});

describe('admin dashboard', () => {
  it('shows every widget from the API with links to the right pages', async () => {
    dashboard.getAdminDashboard.mockResolvedValue(makeAdminDashboard());
    asAdmin(<AdminDashboardPage />);

    expect(await screen.findByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument();
    expect(screen.getByText(/Welcome, Sam/)).toBeInTheDocument();
    expect(screen.queryByText(/arrive with their feature phases/)).not.toBeInTheDocument();

    const people = await region('People and classes');
    expect(people.getByText('420')).toBeInTheDocument();
    expect(people.getByText('35')).toBeInTheDocument();
    expect(people.getByText('Academic year 2026-2027')).toBeInTheDocument();

    const attendance = await region("Today's attendance");
    expect(attendance.getByText('Registers finalized').nextSibling).toHaveTextContent('9');
    expect(attendance.getByText('Registers open').nextSibling).toHaveTextContent('3');

    const fees = await region('Fee snapshot');
    expect(fees.getByText('Outstanding').nextSibling).toHaveTextContent('15,000');
    expect(fees.getByText('6 fees are overdue')).toBeInTheDocument();
    expect(fees.getByRole('link', { name: 'View student fees' })).toHaveAttribute(
      'href',
      '/admin/student-fees',
    );

    const exams = await region('Exams in progress');
    expect(exams.getByText('Open for marks').nextSibling).toHaveTextContent('2');
    expect(exams.getByRole('link', { name: 'View exams' })).toHaveAttribute('href', '/admin/exams');

    const live = await region('Live announcements');
    expect(live.getByText('3')).toBeInTheDocument();

    const activity = await region('Recent activity');
    expect(activity.getByText('Payment reversed')).toBeInTheDocument();
    expect(activity.getByText('Reversed receipt R-0001')).toBeInTheDocument();
    expect(activity.getByText(/Alice Admin/)).toBeInTheDocument();
    expect(activity.getByRole('link', { name: 'View all' })).toHaveAttribute(
      'href',
      '/admin/audit-logs',
    );
    expect(screen.getByRole('heading', { name: 'Backend status' })).toBeInTheDocument();
  });

  it('explains the fee widget when there is no active academic year and shows quiet activity', async () => {
    dashboard.getAdminDashboard.mockResolvedValue(
      makeAdminDashboard({
        academicYearName: null,
        fees: null,
        attendanceToday: { registersFinalized: 0, registersOpen: 0 },
        recentActivity: [],
      }),
    );
    asAdmin(<AdminDashboardPage />);

    const fees = await region('Fee snapshot');
    expect(fees.getByText(/once an academic year is active/)).toBeInTheDocument();
    expect(fees.queryByRole('link')).not.toBeInTheDocument();
    expect(
      (await region("Today's attendance")).getByText(/No registers have been opened/),
    ).toBeInTheDocument();
    expect((await region('Recent activity')).getByText('No recent activity')).toBeInTheDocument();
    expect(screen.getByText('No active academic year')).toBeInTheDocument();
  });

  it('shows one error with retry and keeps the status card when the dashboard fails', async () => {
    dashboard.getAdminDashboard.mockRejectedValueOnce(new ApiError('Boom', { status: 500 }));
    asAdmin(<AdminDashboardPage />);

    expect(await screen.findByText('Boom')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Admin dashboard' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Backend status' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Fee snapshot' })).not.toBeInTheDocument();

    dashboard.getAdminDashboard.mockResolvedValue(makeAdminDashboard());
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('region', { name: 'Fee snapshot' })).toBeInTheDocument();
    expect(screen.queryByText('Boom')).not.toBeInTheDocument();
  });
});

describe('teacher dashboard', () => {
  const asTeacher = () =>
    renderWithProviders(<TeacherDashboardPage />, {
      route: '/teacher',
      auth: { user: makeUser({ roles: ['ROLE_TEACHER'], firstName: 'Tara' }) },
    });

  it('shows the timetable and the work queues with links', async () => {
    dashboard.getTeacherDashboard.mockResolvedValue(makeTeacherDashboard());
    asTeacher();

    expect(await screen.findByRole('heading', { name: 'Teacher dashboard' })).toBeInTheDocument();
    expect(screen.getByText(/Welcome, Tara/)).toBeInTheDocument();

    const timetable = await region("Today's timetable");
    expect(timetable.getByText('Mathematics')).toBeInTheDocument();
    expect(timetable.getByText('09:00 - 09:45')).toBeInTheDocument();
    expect(timetable.getByText(/Grade 8 - A · Room R12/)).toBeInTheDocument();

    expect((await region('Classes taught')).getByText('5')).toBeInTheDocument();
    const review = await region('Submissions awaiting review');
    expect(review.getByText('7')).toBeInTheDocument();
    expect(review.getByRole('link', { name: 'Review submissions' })).toHaveAttribute(
      'href',
      '/teacher/submissions',
    );
    const marks = await region('Marks still to enter');
    expect(marks.getByText('12')).toBeInTheDocument();
    expect(marks.getByRole('link', { name: 'Enter marks' })).toHaveAttribute(
      'href',
      '/teacher/marks',
    );
    const registers = await region('Registers to take today');
    expect(registers.getByText('Grade 8 - A')).toBeInTheDocument();
    expect(registers.getByRole('link', { name: 'Take attendance' })).toHaveAttribute(
      'href',
      '/teacher/attendance',
    );
    expect(
      (await region('Unread announcements')).getByRole('link', { name: 'Open announcements' }),
    ).toHaveAttribute('href', '/teacher/announcements');
  });

  it('shows friendly messages when there is nothing to do today', async () => {
    dashboard.getTeacherDashboard.mockResolvedValue(
      makeTeacherDashboard({ todaySlots: [], registersToTake: [] }),
    );
    asTeacher();

    expect((await region("Today's timetable")).getByText('No periods today')).toBeInTheDocument();
    expect(
      (await region('Registers to take today')).getByText(/No daily registers are waiting/),
    ).toBeInTheDocument();
  });
});

describe('student dashboard', () => {
  const asStudent = () =>
    renderWithProviders(<StudentDashboardPage />, {
      route: '/student',
      auth: { user: makeUser() },
    });

  it('shows class, timetable, attendance, work, result and fees with links', async () => {
    dashboard.getStudentDashboard.mockResolvedValue(makeStudentDashboard());
    asStudent();

    expect(await screen.findByRole('heading', { name: 'Student dashboard' })).toBeInTheDocument();
    expect(screen.getByText(/Welcome, Sam/)).toBeInTheDocument();
    expect((await region('My class')).getByText('Grade 8 - A')).toBeInTheDocument();
    expect((await region("Today's timetable")).getByText('Mathematics')).toBeInTheDocument();
    expect((await region('Attendance this month')).getByText('92.5%')).toBeInTheDocument();

    const work = await region('Upcoming work');
    expect(work.getByRole('link', { name: 'Fractions worksheet' })).toHaveAttribute(
      'href',
      '/student/assignments/asg-1',
    );
    expect(work.getByText('Resubmission requested')).toBeInTheDocument();

    const result = await region('Latest result');
    expect(result.getByRole('link', { name: 'Term 1 exam' })).toHaveAttribute(
      'href',
      '/student/results/exam-1',
    );
    expect(result.getByText('78.5%')).toBeInTheDocument();

    const fees = await region('Fees');
    expect(fees.getByText('Outstanding').nextSibling).toHaveTextContent('1,500');
    expect(fees.getByText('Overdue fees').nextSibling).toHaveTextContent('1');
    expect(fees.getByText(/Next due/)).toBeInTheDocument();
    expect(fees.getByRole('link', { name: 'View fees' })).toHaveAttribute('href', '/student/fees');
    expect(
      (await region('Unread announcements')).getByRole('link', { name: 'Open announcements' }),
    ).toHaveAttribute('href', '/student/announcements');
  });

  it('handles a student with no register, no work and no result yet', async () => {
    dashboard.getStudentDashboard.mockResolvedValue(
      makeStudentDashboard({
        className: null,
        todaySlots: [],
        attendancePercent: null,
        upcomingWork: [],
        latestResult: null,
        fees: { outstanding: 0, overdueFees: 0, nextDueDate: null },
      }),
    );
    asStudent();

    expect(
      (await region('Attendance this month')).getByText('No register finalized yet'),
    ).toBeInTheDocument();
    expect((await region('My class')).getByText(/not enrolled/)).toBeInTheDocument();
    expect((await region("Today's timetable")).getByText('No periods today')).toBeInTheDocument();
    expect((await region('Upcoming work')).getByText('No upcoming work')).toBeInTheDocument();
    expect(
      (await region('Latest result')).getByText(/No result published yet/),
    ).toBeInTheDocument();
    expect((await region('Fees')).getByText('No upcoming due date')).toBeInTheDocument();
  });
});

describe('audit logs page', () => {
  const changed = makeAuditEntry({
    id: 'audit-2',
    action: 'USER_UPDATED',
    entityType: 'User',
    summary: 'Updated user S-100',
    actorName: null,
    oldValues: { firstName: 'Sam', password: '[REDACTED]', status: 'ACTIVE' },
    newValues: { firstName: 'Samuel', password: '[REDACTED]', profile: { grade: 8 } },
  });

  it('lists entries newest first with readable labels and no write controls', async () => {
    audit.listAuditLogs.mockResolvedValue(pageOf([makeAuditEntry(), changed]));
    asAdmin(<AuditLogsPage />, '/admin/audit-logs');

    expect(await screen.findByRole('heading', { name: 'Audit logs' })).toBeInTheDocument();
    expect(await screen.findByText('Reversed receipt R-0001')).toBeInTheDocument();
    expect(screen.getByText('Payment reversed')).toBeInTheDocument();
    expect(screen.getByText('Alice Admin')).toBeInTheDocument();
    expect(screen.getByText('System/unknown')).toBeInTheDocument();
    expect(audit.listAuditLogs).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, sort: 'createdAt', direction: 'desc' }),
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Actions for Reversed receipt R-0001' }),
    );
    const items = screen.getAllByRole('menuitem').map((item) => item.textContent);
    expect(items).toEqual(['Details']);
    expect(screen.queryByRole('button', { name: /delete|edit|remove/i })).not.toBeInTheDocument();
  });

  it('sends the filters as query parameters and resets to the first page', async () => {
    audit.listAuditLogs.mockResolvedValue(pageOf([makeAuditEntry()]));
    asAdmin(<AuditLogsPage />, '/admin/audit-logs');
    await screen.findByText('Reversed receipt R-0001');

    await userEvent.click(screen.getByRole('combobox', { name: 'Action' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Payment reversed' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'Entity type' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Student fee' }));
    await userEvent.type(screen.getByLabelText('From'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('To'), '2026-11-19');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search summary' }), 'receipt');

    await waitFor(() =>
      expect(audit.listAuditLogs).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 0,
          action: 'PAYMENT_REVERSED',
          entityType: 'StudentFee',
          from: '2026-11-01',
          to: '2026-11-19',
          search: 'receipt',
        }),
      ),
    );
  });

  it('shows a filter-aware empty state and a retryable error', async () => {
    audit.listAuditLogs.mockRejectedValueOnce(new ApiError('Boom', { status: 500 }));
    asAdmin(<AuditLogsPage />, '/admin/audit-logs');

    expect(await screen.findByText('Boom')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('No audit entries found')).toBeInTheDocument();
  });

  it('opens the details with before and after values, marking changed rows', async () => {
    audit.listAuditLogs.mockResolvedValue(pageOf([changed]));
    asAdmin(<AuditLogsPage />, '/admin/audit-logs');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Actions for Updated user S-100' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Details' }));

    const dialog = await screen.findByRole('dialog', { name: 'Audit log details' });
    expect(within(dialog).getByText('USER_UPDATED')).toBeInTheDocument();
    expect(within(dialog).getByText('User updated')).toBeInTheDocument();
    expect(within(dialog).getByText('System/unknown')).toBeInTheDocument();
    expect(within(dialog).getByText('10.0.0.5')).toBeInTheDocument();

    const table = within(dialog).getByRole('table', { name: 'Before / After' });
    const rowFor = (field: string) =>
      within(table).getByRole('row', { name: new RegExp(`^${field}`) });
    expect(rowFor('firstName')).toHaveAttribute('data-changed', 'true');
    expect(within(rowFor('firstName')).getByText('Sam')).toBeInTheDocument();
    expect(within(rowFor('firstName')).getByText('Samuel')).toBeInTheDocument();
    expect(rowFor('password')).toHaveAttribute('data-changed', 'false');
    expect(within(rowFor('password')).getAllByText('[REDACTED]')).toHaveLength(2);
    expect(rowFor('status')).toHaveAttribute('data-changed', 'true');
    expect(within(rowFor('profile')).getByText('{"grade":8}')).toBeInTheDocument();
    expect(
      within(dialog).queryByRole('button', { name: /delete|edit|save/i }),
    ).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('says so when an entry recorded no values', async () => {
    audit.listAuditLogs.mockResolvedValue(pageOf([makeAuditEntry()]));
    asAdmin(<AuditLogsPage />, '/admin/audit-logs');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Actions for Reversed receipt R-0001' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Details' }));

    const dialog = await screen.findByRole('dialog', { name: 'Audit log details' });
    expect(within(dialog).getByText('No values were recorded for this entry.')).toBeInTheDocument();
  });
});
