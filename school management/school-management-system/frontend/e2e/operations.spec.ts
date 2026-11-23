import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf, type AuthorityName } from './support';

const MOBILE_BREAKPOINT = 900;
const PHONE_BREAKPOINT = 600;

const widthOf = (testInfo: { project: { use: { viewport?: { width: number } | null } } }) =>
  testInfo.project.use.viewport?.width ?? 0;

const entry = (id: string, day: string, subject: string, start: string, end: string) => ({
  id,
  academicYearId: 'y1',
  academicYearName: '2026-2027',
  sectionId: 'sec1',
  sectionName: 'Grade 8 - A',
  subjectId: `s-${subject}`,
  subjectCode: subject.slice(0, 4).toUpperCase(),
  subjectName: subject,
  teacherId: 't1',
  teacherName: 'Tara Teacher with a rather long name',
  room: 'Room 101',
  dayOfWeek: day,
  periodNumber: 1,
  startTime: start,
  endTime: end,
  effectiveFrom: '2026-06-01',
  effectiveTo: '2027-03-31',
  active: true,
  date: '2026-11-16',
  version: 0,
});
const entries = [
  entry('e1', 'MONDAY', 'Mathematics', '09:00:00', '09:45:00'),
  entry('e2', 'MONDAY', 'English', '10:00:00', '10:45:00'),
  entry('e3', 'TUESDAY', 'Science', '09:00:00', '09:45:00'),
  entry('e4', 'FRIDAY', 'Computer Science', '11:00:00', '11:45:00'),
];
const week = { weekStart: '2026-11-16', weekEnd: '2026-11-22', entries };

const year = {
  id: 'y1',
  name: '2026-2027',
  startDate: '2026-06-01',
  endDate: '2027-03-31',
  status: 'ACTIVE',
  version: 0,
};
const section = {
  id: 'sec1',
  academicYear: { id: 'y1', name: '2026-2027' },
  gradeLevel: { id: 'g1', name: 'Grade 8' },
  name: 'A',
  capacity: 40,
  classTeacher: null,
  active: true,
  version: 0,
};
const assignment = {
  id: 'a1',
  academicYearId: 'y1',
  academicYearName: '2026-2027',
  sectionId: 'sec1',
  sectionName: 'A',
  gradeLevelName: 'Grade 8',
  subject: { id: 's-Mathematics', code: 'MATH', name: 'Mathematics' },
  teacher: { id: 't1', name: 'Tara Teacher' },
  weeklyPeriods: 5,
  active: true,
  version: 0,
};

const session = (status: 'OPEN' | 'FINALIZED', marked: boolean, annAbsent = false) => ({
  session: {
    id: 'ses1',
    date: '2026-11-18',
    type: 'DAILY',
    sectionId: 'sec1',
    sectionName: 'Grade 8 - A',
    subjectId: null,
    subjectName: null,
    periodNumber: null,
    status,
    counts: { present: marked ? 2 : 0, absent: 0, late: 0, halfDay: 0, excused: 0 },
    enrolled: 2,
    finalizedAt: status === 'FINALIZED' ? '2026-11-18T10:00:00Z' : null,
    version: 0,
  },
  entries: ['Ann Pupil', 'Bob Pupil with a really quite long surname indeed'].map(
    (name, index) => ({
      studentId: `st${index + 1}`,
      admissionNumber: `S-${index + 1}`,
      name,
      rollNumber: index + 1,
      status: marked ? (annAbsent && index === 0 ? 'ABSENT' : 'PRESENT') : null,
      remarks: null,
    }),
  ),
});

async function mockAcademics(page: Page, role: AuthorityName) {
  await mockBackend(page, { role, signedIn: true });
  const base = '**/api/v1/admin/';
  await page.route(`${base}academic-years**`, (route) => route.fulfill({ json: pageOf([year]) }));
  await page.route(`${base}sections**`, (route) => route.fulfill({ json: pageOf([section]) }));
  await page.route(`${base}section-subjects**`, (route) =>
    route.fulfill({ json: pageOf([assignment]) }),
  );
  await page.route(`${base}teacher-options**`, (route) => route.fulfill({ json: pageOf([]) }));
}

test('the weekly timetable is a grid on large screens and day tabs on small ones', async ({
  page,
}, testInfo) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.route('**/api/v1/student/timetable**', (route) => route.fulfill({ json: week }));
  await page.goto('/student/timetable');
  await expect(page.getByRole('heading', { name: 'My timetable' })).toBeVisible();

  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await expect(page.getByRole('tablist', { name: 'Day of the week' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Monday' })).toHaveCount(0);
    await page.getByRole('tab', { name: 'Tuesday' }).click();
    await expect(page.getByText('Science')).toBeVisible();
  } else {
    await expect(page.getByRole('region', { name: 'Monday' })).toBeVisible();
    await expect(
      page.getByRole('region', { name: 'Friday' }).getByText('Computer Science'),
    ).toBeVisible();
  }
  await expectNoHorizontalOverflow(page);
});

test('the administrator timetable page fits and its slot dialog validates', async ({
  page,
}, testInfo) => {
  await mockAcademics(page, 'ROLE_ADMIN');
  await page.route('**/api/v1/admin/timetable-entries**', (route) =>
    route.fulfill({ json: pageOf(entries) }),
  );
  await page.goto('/admin/timetable');
  await page.getByRole('combobox', { name: /^class/i }).click();
  await page.getByRole('option', { name: 'Grade 8 - A' }).click();
  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await page.getByRole('tab', { name: 'Monday' }).click();
    await expect(page.getByText('Mathematics').first()).toBeVisible();
  } else {
    await expect(page.getByText('Computer Science').first()).toBeVisible();
  }
  await expectNoHorizontalOverflow(page);

  await page.getByRole('button', { name: 'Add slot' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  const viewport = page.viewportSize()!;
  const box = (await dialog.boundingBox())!;
  if (widthOf(testInfo) < PHONE_BREAKPOINT) {
    expect(box.width).toBeGreaterThanOrEqual(viewport.width - 1);
  } else {
    expect(box.width).toBeLessThan(viewport.width);
  }
  await dialog.getByRole('button', { name: 'Add slot' }).click();
  await expect(dialog.getByText('Subject is required')).toBeVisible();
  await expect(dialog.getByText('Start time is required')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a teacher takes a register, saves it and finalizes it at any width', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_TEACHER', signedIn: true });
  let saved: unknown;
  let state: 'unmarked' | 'marked' | 'finalized' = 'unmarked';
  await page.route('**/api/v1/teacher/attendance/classes', (route) =>
    route.fulfill({
      json: [
        {
          sectionId: 'sec1',
          sectionName: 'Grade 8 - A',
          academicYear: '2026-2027',
          daily: true,
          subjects: [{ id: 's-Mathematics', name: 'Mathematics' }],
        },
      ],
    }),
  );
  await page.route('**/api/v1/teacher/attendance/reports/monthly**', (route) =>
    route.fulfill({
      json: {
        from: '2026-11-01',
        to: '2026-11-30',
        type: 'DAILY',
        threshold: 75,
        truncated: false,
        rows: [],
      },
    }),
  );
  await page.route('**/api/v1/teacher/attendance/sessions**', (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (url.pathname.endsWith('/records') && method === 'PUT') {
      saved = route.request().postDataJSON();
      state = 'marked';
      return route.fulfill({ json: session('OPEN', true, true) });
    }
    if (url.pathname.endsWith('/finalize')) {
      state = 'finalized';
      return route.fulfill({ json: session('FINALIZED', true, true) });
    }
    if (url.pathname.endsWith('/sessions') && method === 'POST') {
      return route.fulfill({ status: 201, json: session('OPEN', false) });
    }
    if (url.pathname.endsWith('/sessions')) return route.fulfill({ json: pageOf([]) });
    return route.fulfill({
      json:
        state === 'unmarked'
          ? session('OPEN', false)
          : session(state === 'marked' ? 'OPEN' : 'FINALIZED', true, true),
    });
  });
  await page.goto('/teacher/attendance');
  await page.getByRole('button', { name: 'Open register' }).click();
  await expect(page.getByRole('list', { name: 'Students' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('button', { name: 'Mark unmarked as present' }).click();
  await page.getByRole('button', { name: 'Absent for Ann Pupil' }).click();
  await expectNoHorizontalOverflow(page);
  await page.getByRole('button', { name: 'Save marks' }).click();
  await expect(page.getByText('Attendance saved')).toBeVisible();
  expect(saved).toEqual({
    records: [
      { studentId: 'st1', status: 'ABSENT', remarks: null },
      { studentId: 'st2', status: 'PRESENT', remarks: null },
    ],
  });

  await page.getByRole('button', { name: 'Finalize' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Finalize' }).click();
  await expect(page.getByText(/Only an administrator can correct it/)).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a student sees their attendance without overflow', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.route('**/api/v1/student/attendance**', (route) =>
    route.fulfill({
      json: {
        month: '2026-11',
        threshold: 75,
        daily: { present: 6, late: 1, halfDay: 0, absent: 3, excused: 0, percentage: 70 },
        subjectWise: { present: 2, late: 0, halfDay: 0, absent: 0, excused: 0, percentage: 100 },
        days: [
          {
            date: '2026-11-17',
            type: 'DAILY',
            subjectName: null,
            periodNumber: null,
            status: 'ABSENT',
            remarks: 'Fever and a long explanation from home',
          },
          {
            date: '2026-11-16',
            type: 'SUBJECT',
            subjectName: 'Computer Science',
            periodNumber: 2,
            status: 'PRESENT',
            remarks: null,
          },
        ],
      },
    }),
  );
  await page.goto('/student/attendance');

  await expect(page.getByText('70.0%')).toBeVisible();
  await expect(page.getByText('Below 75%')).toBeVisible();
  await expect(
    page.getByRole('list', { name: 'Attendance by day' }).getByRole('listitem'),
  ).toHaveCount(2);
  await expectNoHorizontalOverflow(page);
});

test('administrator attendance reports are a table or cards and never overflow', async ({
  page,
}, testInfo) => {
  await mockAcademics(page, 'ROLE_ADMIN');
  await page.route('**/api/v1/admin/attendance/reports/below-threshold**', (route) =>
    route.fulfill({
      json: {
        from: '2026-11-01',
        to: '2026-11-19',
        type: 'DAILY',
        threshold: 75,
        truncated: false,
        rows: [
          {
            studentId: 'st2',
            admissionNumber: 'S-2',
            name: 'Bob Pupil with a really quite long surname indeed',
            sectionName: 'Grade 8 - A',
            present: 1,
            late: 0,
            halfDay: 0,
            absent: 3,
            excused: 0,
            counted: 4,
            percentage: 25,
            belowThreshold: true,
          },
        ],
      },
    }),
  );
  await page.goto('/admin/attendance-reports');
  await expect(page.getByRole('heading', { name: 'Attendance reports' })).toBeVisible();
  await page.getByRole('tab', { name: 'Below threshold' }).click();

  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await expect(
      page.getByRole('list', { name: 'Students below the attendance threshold' }),
    ).toBeVisible();
  } else {
    await expect(
      page.getByRole('table', { name: 'Students below the attendance threshold' }),
    ).toBeVisible();
  }
  await expect(page.getByText('25.0%')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
