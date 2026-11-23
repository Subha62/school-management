import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../../api/errors';
import { WeeklyTimetable } from '../../features/operations/components/WeeklyTimetable';
import { makeAssignment, makeSection, makeTeacher, makeYear, pageOf } from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import type {
  AttendanceDetail,
  AttendanceSummary,
  StudentMonth,
  TimetableEntry,
} from '../../types/operations';
import { AttendanceReportsPage } from '../admin/AttendanceReportsPage';
import { TimetablePage } from '../admin/TimetablePage';
import { StudentAttendancePage } from '../student/StudentAttendancePage';
import { StudentTimetablePage } from '../student/StudentTimetablePage';
import { TeacherAttendancePage } from '../teacher/TeacherAttendancePage';

const ops = vi.hoisted(() => ({
  listTimetableEntries: vi.fn(),
  saveTimetableEntry: vi.fn(),
  deleteTimetableEntry: vi.fn(),
  getSectionWeek: vi.fn(),
  getTeacherWeekForAdmin: vi.fn(),
  getMyTeacherWeek: vi.fn(),
  getMyStudentWeek: vi.fn(),
  listMyClasses: vi.fn(),
  listMySessions: vi.fn(),
  openSession: vi.fn(),
  getMySession: vi.fn(),
  markAttendance: vi.fn(),
  finalizeSession: vi.fn(),
  deleteSession: vi.fn(),
  getTeacherMonthly: vi.fn(),
  listSessions: vi.fn(),
  getAdminSession: vi.fn(),
  listCorrections: vi.fn(),
  correctAttendance: vi.fn(),
  getAdminMonthly: vi.fn(),
  getBelowThreshold: vi.fn(),
  getMyAttendance: vi.fn(),
}));
const academics = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  listSections: vi.fn(),
  listAssignments: vi.fn(),
  listTeacherOptions: vi.fn(),
}));
vi.mock('../../features/operations/api', () => ops);
vi.mock('../../features/academics/api', () => academics);

const entry = (overrides: Partial<TimetableEntry> = {}): TimetableEntry => ({
  id: 'entry-1',
  academicYearId: 'year-1',
  academicYearName: '2026-2027',
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  subjectId: 'subject-1',
  subjectCode: 'MATH',
  subjectName: 'Mathematics',
  teacherId: 'teacher-1',
  teacherName: 'Tara Teacher',
  room: 'R1',
  dayOfWeek: 'MONDAY',
  periodNumber: 1,
  startTime: '09:00:00',
  endTime: '09:45:00',
  effectiveFrom: '2026-06-01',
  effectiveTo: '2027-03-31',
  active: true,
  date: null,
  version: 0,
  ...overrides,
});

const summary = (overrides: Partial<AttendanceSummary> = {}): AttendanceSummary => ({
  id: 'session-1',
  date: '2026-11-18',
  type: 'DAILY',
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  subjectId: null,
  subjectName: null,
  periodNumber: null,
  status: 'OPEN',
  counts: { present: 0, absent: 0, late: 0, halfDay: 0, excused: 0 },
  enrolled: 2,
  finalizedAt: null,
  version: 0,
  ...overrides,
});

const unmarked: AttendanceDetail = {
  session: summary(),
  entries: [
    {
      studentId: 'st-1',
      admissionNumber: 'S-1',
      name: 'Ann Pupil',
      rollNumber: 1,
      status: null,
      remarks: null,
    },
    {
      studentId: 'st-2',
      admissionNumber: 'S-2',
      name: 'Bob Pupil',
      rollNumber: 2,
      status: null,
      remarks: null,
    },
  ],
};
const marked: AttendanceDetail = {
  session: summary({ counts: { present: 1, absent: 1, late: 0, halfDay: 0, excused: 0 } }),
  entries: [
    { ...unmarked.entries[0]!, status: 'PRESENT' },
    { ...unmarked.entries[1]!, status: 'ABSENT', remarks: 'Fever' },
  ],
};

function renderAs(role: 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT', ui: React.ReactElement) {
  return renderWithProviders(ui, { auth: { user: makeUser({ id: 'me', roles: [role] }) } });
}

beforeEach(() => {
  setViewportWidth(1440);
  [...Object.values(ops), ...Object.values(academics)].forEach((mock) => mock.mockReset());
  academics.listAcademicYears.mockResolvedValue(pageOf([makeYear({ status: 'ACTIVE' })]));
  academics.listSections.mockResolvedValue(pageOf([makeSection()]));
  academics.listAssignments.mockResolvedValue(
    pageOf([makeAssignment({ teacher: { id: 'teacher-1', name: 'Tara Teacher' } })]),
  );
  academics.listTeacherOptions.mockResolvedValue(pageOf([makeTeacher()]));
});

describe('WeeklyTimetable', () => {
  const entries = [
    entry(),
    entry({
      id: 'entry-2',
      dayOfWeek: 'WEDNESDAY',
      subjectName: 'English',
      startTime: '10:00:00',
      endTime: '10:45:00',
    }),
  ];

  it('shows a column per day on large screens, with empty days marked', () => {
    renderAs(
      'ROLE_STUDENT',
      <WeeklyTimetable
        entries={entries}
        detail={(e) => e.teacherName ?? ''}
        emptyTitle="Nothing"
      />,
    );

    expect(
      within(screen.getByRole('region', { name: 'Monday' })).getByText('Mathematics'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Wednesday' })).getByText('English'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: 'Tuesday' })).getByText('Nothing scheduled'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Sunday' })).not.toBeInTheDocument();
  });

  it('shows one day at a time with a day selector on phones', async () => {
    setViewportWidth(375);
    renderAs(
      'ROLE_STUDENT',
      <WeeklyTimetable
        entries={entries}
        detail={(e) => e.teacherName ?? ''}
        emptyTitle="Nothing"
        initialDay="WEDNESDAY"
      />,
    );

    expect(screen.getByText('English')).toBeInTheDocument();
    expect(screen.queryByText('Mathematics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Monday' }));
    expect(screen.getByText('Mathematics')).toBeInTheDocument();
    expect(screen.queryByText('English')).not.toBeInTheDocument();
  });

  it('explains an empty week', () => {
    renderAs(
      'ROLE_STUDENT',
      <WeeklyTimetable entries={[]} detail={() => ''} emptyTitle="No classes this week" />,
    );
    expect(screen.getByText('No classes this week')).toBeInTheDocument();
  });
});

describe('StudentTimetablePage', () => {
  it('asks for the current week and shows the teacher of each slot', async () => {
    ops.getMyStudentWeek.mockResolvedValue({
      weekStart: '2026-11-16',
      weekEnd: '2026-11-22',
      entries: [entry({ date: '2026-11-16' })],
    });
    renderAs('ROLE_STUDENT', <StudentTimetablePage />);

    expect(await screen.findByText('Mathematics')).toBeInTheDocument();
    expect(screen.getByText(/Tara Teacher · R1/)).toBeInTheDocument();
    expect(ops.getMyStudentWeek).toHaveBeenCalledWith(expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
    await userEvent.click(screen.getByRole('button', { name: 'Next week' }));
    await waitFor(() => expect(ops.getMyStudentWeek).toHaveBeenCalledTimes(2));
  });
});

describe('TimetablePage (administrator)', () => {
  beforeEach(() => {
    ops.listTimetableEntries.mockResolvedValue(pageOf([entry()]));
  });

  async function chooseClass() {
    await userEvent.click(await screen.findByRole('combobox', { name: /^class/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8 - A' }));
  }

  async function fillSlot(dialog: HTMLElement) {
    await userEvent.click(within(dialog).getByRole('combobox', { name: /^subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'MATH - Mathematics' }));
    fireEvent.change(within(dialog).getByLabelText(/start time/i), { target: { value: '09:00' } });
    fireEvent.change(within(dialog).getByLabelText(/end time/i), { target: { value: '09:45' } });
  }

  it('adds a slot for the chosen class and sends blank optional values as null', async () => {
    ops.saveTimetableEntry.mockResolvedValue(entry({ id: 'new' }));
    renderAs('ROLE_ADMIN', <TimetablePage />);
    expect(await screen.findByText(/Choose an academic year and a class/)).toBeInTheDocument();
    await chooseClass();
    expect(await screen.findByText('Tara Teacher · R1')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add slot' }));
    const dialog = await screen.findByRole('dialog');
    await fillSlot(dialog);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add slot' }));

    await waitFor(() =>
      expect(ops.saveTimetableEntry).toHaveBeenCalledWith(undefined, {
        sectionId: 'section-1',
        subjectId: 'subject-1',
        teacherId: null,
        room: null,
        dayOfWeek: 'MONDAY',
        periodNumber: 1,
        startTime: '09:00',
        endTime: '09:45',
        effectiveFrom: null,
        effectiveTo: null,
        active: true,
        version: undefined,
      }),
    );
  });

  it('shows a clash reported by the server inside the dialog and keeps it open', async () => {
    ops.saveTimetableEntry.mockRejectedValue(
      new ApiError(
        'Tara Teacher is already teaching Grade 8 - B (Mathematics) on Monday from 09:00 to 09:45.',
        {
          status: 409,
        },
      ),
    );
    renderAs('ROLE_ADMIN', <TimetablePage />);
    await chooseClass();
    await userEvent.click(await screen.findByRole('button', { name: 'Add slot' }));
    const dialog = await screen.findByRole('dialog');
    await fillSlot(dialog);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add slot' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('is already teaching');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('rejects an end time that is not after the start time before calling the server', async () => {
    renderAs('ROLE_ADMIN', <TimetablePage />);
    await chooseClass();
    await userEvent.click(await screen.findByRole('button', { name: 'Add slot' }));
    const dialog = await screen.findByRole('dialog');
    await fillSlot(dialog);
    fireEvent.change(within(dialog).getByLabelText(/end time/i), { target: { value: '08:00' } });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add slot' }));

    expect(
      await within(dialog).findByText('End time must be after the start time'),
    ).toBeInTheDocument();
    expect(ops.saveTimetableEntry).not.toHaveBeenCalled();
  });

  it('confirms before deleting a slot', async () => {
    ops.deleteTimetableEntry.mockResolvedValue(undefined);
    renderAs('ROLE_ADMIN', <TimetablePage />);
    await chooseClass();
    await userEvent.click(await screen.findByRole('button', { name: /Delete Mathematics Monday/ }));
    const dialog = await screen.findByRole('dialog', { name: /delete timetable slot/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(ops.deleteTimetableEntry).toHaveBeenCalledWith('entry-1'));
  });

  it('makes the timetable of a closed year read-only', async () => {
    academics.listAcademicYears.mockResolvedValue(pageOf([makeYear({ status: 'CLOSED' })]));
    renderAs('ROLE_ADMIN', <TimetablePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: /academic year/i }));
    await userEvent.click(await screen.findByRole('option', { name: '2026-2027' }));
    await chooseClass();
    expect(await screen.findByText(/is closed, so its timetable is read-only/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add slot' })).toBeDisabled();
  });
});

describe('TeacherAttendancePage', () => {
  let current: AttendanceDetail;

  beforeEach(() => {
    current = unmarked;
    ops.listMyClasses.mockResolvedValue([
      {
        sectionId: 'section-1',
        sectionName: 'Grade 8 - A',
        academicYear: '2026-2027',
        daily: true,
        subjects: [{ id: 'subject-1', name: 'Mathematics' }],
      },
    ]);
    ops.listMySessions.mockResolvedValue(pageOf([]));
    ops.getTeacherMonthly.mockResolvedValue({
      from: '2026-11-01',
      to: '2026-11-30',
      type: 'DAILY',
      threshold: 75,
      truncated: false,
      rows: [],
    });
    ops.openSession.mockImplementation(() => Promise.resolve(current));
    ops.getMySession.mockImplementation(() => Promise.resolve(current));
  });

  it('opens a register, marks the unmarked as present, saves, then finalizes', async () => {
    ops.markAttendance.mockImplementation(() => {
      current = marked;
      return Promise.resolve(marked);
    });
    ops.finalizeSession.mockResolvedValue({
      ...marked,
      session: { ...marked.session, status: 'FINALIZED' },
    });
    renderAs('ROLE_TEACHER', <TeacherAttendancePage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Open register' }));
    expect(await screen.findByRole('list', { name: 'Students' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finalize' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save marks' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Absent for Bob Pupil' }));
    await userEvent.type(screen.getByLabelText('Remarks for Bob Pupil'), 'Fever');
    await userEvent.click(screen.getByRole('button', { name: 'Mark unmarked as present' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));

    await waitFor(() =>
      expect(ops.markAttendance).toHaveBeenCalledWith('session-1', [
        { studentId: 'st-1', status: 'PRESENT', remarks: null },
        { studentId: 'st-2', status: 'ABSENT', remarks: 'Fever' },
      ]),
    );
    const finalize = screen.getByRole('button', { name: 'Finalize' });
    await waitFor(() => expect(finalize).toBeEnabled());
    await userEvent.click(finalize);
    const dialog = await screen.findByRole('dialog', { name: /finalize attendance/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Finalize' }));

    await waitFor(() => expect(ops.finalizeSession).toHaveBeenCalledWith('session-1'));
  });

  it('shows the reason when the server refuses the marks', async () => {
    ops.markAttendance.mockRejectedValue(
      new ApiError(
        '1 student is not enrolled in this section, so attendance cannot be marked for them.',
        {
          status: 422,
        },
      ),
    );
    renderAs('ROLE_TEACHER', <TeacherAttendancePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Open register' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Mark unmarked as present' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));

    expect(await screen.findByText(/is not enrolled in this section/)).toBeInTheDocument();
  });

  it('offers the daily register and each subject, and opens a subject register with its period', async () => {
    renderAs('ROLE_TEACHER', <TeacherAttendancePage />);
    await userEvent.click(await screen.findByRole('combobox', { name: /attendance for/i }));
    expect(await screen.findByRole('option', { name: /whole day/i })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: 'Mathematics' }));
    await userEvent.type(screen.getByLabelText(/period/i), '2');
    await userEvent.click(screen.getByRole('button', { name: 'Open register' }));

    await waitFor(() =>
      expect(ops.openSession).toHaveBeenCalledWith({
        sectionId: 'section-1',
        date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        subjectId: 'subject-1',
        periodNumber: 2,
      }),
    );
  });

  it('shows a finalized register read-only', async () => {
    current = { ...marked, session: { ...marked.session, status: 'FINALIZED' } };
    renderAs('ROLE_TEACHER', <TeacherAttendancePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Open register' }));

    expect(await screen.findByText(/Only an administrator can correct it/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save marks' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Absent for/ })).not.toBeInTheDocument();
  });

  it('explains when the teacher has no classes', async () => {
    ops.listMyClasses.mockResolvedValue([]);
    renderAs('ROLE_TEACHER', <TeacherAttendancePage />);
    expect(
      await screen.findByText(/not the class teacher or a subject teacher/),
    ).toBeInTheDocument();
  });
});

describe('StudentAttendancePage', () => {
  it('shows the rates, warns below the threshold and lists the days', async () => {
    const month: StudentMonth = {
      month: '2026-11',
      threshold: 75,
      daily: { present: 6, late: 1, halfDay: 0, absent: 3, excused: 0, percentage: 70 },
      subjectWise: { present: 0, late: 0, halfDay: 0, absent: 0, excused: 0, percentage: null },
      days: [
        {
          date: '2026-11-17',
          type: 'DAILY',
          subjectName: null,
          periodNumber: null,
          status: 'ABSENT',
          remarks: 'Fever',
        },
        {
          date: '2026-11-16',
          type: 'SUBJECT',
          subjectName: 'Mathematics',
          periodNumber: 2,
          status: 'PRESENT',
          remarks: null,
        },
      ],
    };
    ops.getMyAttendance.mockResolvedValue(month);
    renderAs('ROLE_STUDENT', <StudentAttendancePage />);

    expect(await screen.findByText('70.0%')).toBeInTheDocument();
    expect(screen.getByText('Below 75%')).toBeInTheDocument();
    expect(screen.getByText('No data')).toBeInTheDocument();
    const days = screen.getByRole('list', { name: 'Attendance by day' });
    expect(within(days).getByText(/Daily · Fever|Daily/)).toBeInTheDocument();
    expect(within(days).getByText(/Mathematics · period 2/)).toBeInTheDocument();
    expect(within(days).getByText('Absent')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }));
    await waitFor(() => expect(ops.getMyAttendance).toHaveBeenCalledTimes(2));
  });

  it('says so when nothing has been finalized', async () => {
    ops.getMyAttendance.mockResolvedValue({
      month: '2026-11',
      threshold: 75,
      daily: { present: 0, late: 0, halfDay: 0, absent: 0, excused: 0, percentage: null },
      subjectWise: { present: 0, late: 0, halfDay: 0, absent: 0, excused: 0, percentage: null },
      days: [],
    });
    renderAs('ROLE_STUDENT', <StudentAttendancePage />);
    expect(await screen.findByText('No attendance recorded')).toBeInTheDocument();
  });
});

describe('AttendanceReportsPage (administrator)', () => {
  const row = {
    studentId: 'st-2',
    admissionNumber: 'S-2',
    name: 'Bob Pupil',
    sectionName: 'Grade 8 - A',
    present: 1,
    late: 0,
    halfDay: 0,
    absent: 3,
    excused: 0,
    counted: 4,
    percentage: 25,
    belowThreshold: true,
  };

  it('lists the students below the threshold with their class and truncation notice', async () => {
    ops.getBelowThreshold.mockResolvedValue({
      from: '2026-11-01',
      to: '2026-11-19',
      type: 'DAILY',
      threshold: 75,
      truncated: true,
      rows: [row],
    });
    renderAs('ROLE_ADMIN', <AttendanceReportsPage />);
    await userEvent.click(screen.getByRole('tab', { name: 'Below threshold' }));

    const table = await screen.findByRole('table', {
      name: 'Students below the attendance threshold',
    });
    expect(within(table).getByText('Bob Pupil')).toBeInTheDocument();
    expect(within(table).getByText('25.0%')).toBeInTheDocument();
    expect(screen.getByText(/Only the 500 lowest students are shown/)).toBeInTheDocument();
  });

  it('validates the threshold before asking the server', async () => {
    ops.getBelowThreshold.mockResolvedValue({
      from: '',
      to: '',
      type: 'DAILY',
      threshold: 75,
      truncated: false,
      rows: [],
    });
    renderAs('ROLE_ADMIN', <AttendanceReportsPage />);
    await userEvent.click(screen.getByRole('tab', { name: 'Below threshold' }));
    await screen.findByText('Nobody is below the threshold');

    await userEvent.type(screen.getByLabelText('Below (%)'), '150');
    expect(await screen.findByText('Enter a whole number from 1 to 100')).toBeInTheDocument();
    await waitFor(() => {
      const last = ops.getBelowThreshold.mock.calls.at(-1)?.[0] as { threshold?: number };
      expect(last.threshold).toBeUndefined();
    });
  });

  it('corrects a finalized mark only with a reason', async () => {
    const finalized = { ...marked, session: { ...marked.session, status: 'FINALIZED' as const } };
    ops.listSessions.mockResolvedValue(pageOf([finalized.session]));
    ops.getAdminSession.mockResolvedValue(finalized);
    ops.listCorrections.mockResolvedValue([
      {
        id: 'c1',
        studentId: 'st-1',
        studentName: 'Ann Pupil',
        admissionNumber: 'S-1',
        previousStatus: 'ABSENT',
        newStatus: 'PRESENT',
        reason: 'Marked by mistake',
        correctedBy: 'Ada Admin',
        correctedAt: '2026-11-19T10:00:00Z',
      },
    ]);
    ops.correctAttendance.mockResolvedValue(finalized);
    renderAs('ROLE_ADMIN', <AttendanceReportsPage />);
    await userEvent.click(screen.getByRole('tab', { name: 'Sessions and corrections' }));
    await userEvent.click(
      await screen.findByRole('button', { name: /Actions for Grade 8 - A 2026-11-18/ }),
    );
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Review or correct' }));

    const review = await screen.findByRole('dialog', { name: /Grade 8 - A/ });
    expect(await within(review).findByText(/Marked by mistake/)).toBeInTheDocument();
    await userEvent.click(
      within(review).getByRole('button', { name: 'Correct attendance of Bob Pupil' }),
    );
    const dialog = await screen.findByRole('dialog', { name: /correct attendance: bob pupil/i });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Save correction' }));
    expect(
      await within(dialog).findByText('Explain the reason in at least 5 characters'),
    ).toBeInTheDocument();
    expect(ops.correctAttendance).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('combobox', { name: /new status/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Excused' }));
    await userEvent.clear(within(dialog).getByLabelText(/^remarks/i));
    await userEvent.type(
      within(dialog).getByLabelText(/reason for the correction/i),
      'Medical certificate',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save correction' }));

    await waitFor(() =>
      expect(ops.correctAttendance).toHaveBeenCalledWith('session-1', {
        studentId: 'st-2',
        status: 'EXCUSED',
        remarks: null,
        reason: 'Medical certificate',
      }),
    );
  });
});
