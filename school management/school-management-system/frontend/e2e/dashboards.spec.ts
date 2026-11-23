import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf, type AuthorityName } from './support';

const longText =
  'Reassigned the teacher of Mathematics for the very long named class Grade 8 - Section A';

async function answer(page: Page, path: RegExp, body: unknown) {
  await page.route(
    (url) => path.test(url.pathname),
    (route) => route.fulfill({ json: body }),
  );
}

async function signedIn(page: Page, role: AuthorityName) {
  await mockBackend(page, { role, signedIn: true });
  await answer(page, /\/api\/v1\/announcements\/unread-count$/, { count: 2 });
}

const entry = (overrides: Record<string, unknown> = {}) => ({
  id: 'log-1',
  createdAt: '2026-11-19T10:15:00Z',
  action: 'GRADE_RULE_UPDATED',
  entityType: 'GradeRule',
  entityId: 'r-1',
  summary: longText,
  actorId: 'u-1',
  actorName: 'Asha Admin',
  ipAddress: '127.0.0.1',
  userAgent: 'Mozilla/5.0',
  oldValues: { minPercent: 80, password: '[REDACTED]' },
  newValues: { minPercent: 85, password: '[REDACTED]' },
  ...overrides,
});

const slot = (n: number, subject: string) => ({
  periodNumber: n,
  startTime: `0${8 + n}:00:00`,
  endTime: `0${8 + n}:45:00`,
  subjectName: subject,
  sectionName: 'Grade 8 - A',
  room: 'Room 101',
});

test('the administrator dashboard shows live numbers and recent activity', async ({ page }) => {
  await signedIn(page, 'ROLE_ADMIN');
  await answer(page, /\/api\/v1\/admin\/dashboard$/, {
    academicYearName: '2026-2027',
    activeStudents: 480,
    activeTeachers: 32,
    activeClasses: 16,
    attendanceToday: { registersFinalized: 12, registersOpen: 4 },
    fees: {
      billed: 1200000,
      collected: 800000,
      outstanding: 400000,
      overdue: 90000,
      overdueFees: 14,
    },
    exams: { openForMarks: 1, awaitingPublication: 2, published: 3 },
    liveAnnouncements: 5,
    recentActivity: [
      entry(),
      entry({
        id: 'log-2',
        action: 'PAYMENT_RECORDED',
        summary: 'Receipt REC-2026-000001',
        actorName: null,
      }),
    ],
  });
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: 'Admin dashboard' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Fee snapshot' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Recent activity' })).toContainText(
    'Receipt REC-2026-000001',
  );
  await expect(page.getByText('480').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the dashboard keeps the status card and offers a retry when it cannot load', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_ADMIN');
  await page.route(
    (url) => /\/api\/v1\/admin\/dashboard$/.test(url.pathname),
    (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/problem+json',
        body: JSON.stringify({ title: 'Error', status: 500, detail: 'boom' }),
      }),
  );
  await page.goto('/admin');
  await expect(page.getByText('Unable to load the dashboard')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
  await expect(page.getByText('Backend status')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the teacher dashboard lists today and what is waiting', async ({ page }) => {
  await signedIn(page, 'ROLE_TEACHER');
  await answer(page, /\/api\/v1\/teacher\/dashboard$/, {
    todaySlots: [slot(1, 'Mathematics'), slot(2, 'Computer Science and Applications')],
    classesTaught: 3,
    awaitingReview: 7,
    marksMissing: 12,
    registersToTake: ['Grade 8 - A'],
    unreadAnnouncements: 2,
  });
  await page.goto('/teacher');
  await expect(page.getByRole('heading', { name: 'Teacher dashboard' })).toBeVisible();
  await expect(page.getByRole('region', { name: "Today's timetable" })).toContainText(
    'Mathematics',
  );
  await expect(page.getByRole('link', { name: 'Review submissions' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the student dashboard shows work, results and fees', async ({ page }) => {
  await signedIn(page, 'ROLE_STUDENT');
  await answer(page, /\/api\/v1\/student\/dashboard$/, {
    className: 'Grade 8 - A',
    todaySlots: [],
    attendancePercent: 92.5,
    upcomingWork: [
      {
        assignmentId: 'as-1',
        title: 'Worksheet 1 with a rather long title to wrap',
        subjectName: 'Mathematics',
        dueAt: '2099-01-02T09:30:00Z',
        resubmission: false,
      },
    ],
    latestResult: {
      examId: 'exam-1',
      examName: 'Term 1',
      percentage: 86.67,
      grade: 'A',
      result: 'PASS',
    },
    fees: { outstanding: 10000, overdueFees: 1, nextDueDate: '2026-12-10' },
    unreadAnnouncements: 3,
  });
  await page.goto('/student');
  await expect(page.getByRole('heading', { name: 'Student dashboard' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Worksheet 1/ })).toHaveAttribute(
    'href',
    '/student/assignments/as-1',
  );
  await expect(page.getByRole('link', { name: 'Term 1' })).toHaveAttribute(
    'href',
    '/student/results/exam-1',
  );
  await expect(page.getByText('No periods today')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator browses the audit trail, filters it and reads a change', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_ADMIN');
  await answer(page, /\/api\/v1\/admin\/audit-logs\/actions$/, [
    'GRADE_RULE_UPDATED',
    'PAYMENT_RECORDED',
  ]);
  await answer(page, /\/api\/v1\/admin\/audit-logs\/entity-types$/, ['GradeRule', 'Payment']);
  await answer(page, /\/api\/v1\/admin\/audit-logs\/log-1$/, entry());
  const queries: string[] = [];
  await page.route(
    (url) => /\/api\/v1\/admin\/audit-logs$/.test(url.pathname),
    (route) => {
      queries.push(new URL(route.request().url()).search);
      return route.fulfill({
        json: pageOf([
          entry(),
          entry({ id: 'log-2', actorName: null, oldValues: null, newValues: null }),
        ]),
      });
    },
  );
  await page.goto('/admin/audit-logs');
  await expect(page.getByRole('heading', { name: 'Audit logs' })).toBeVisible();
  await expect(page.getByText('System/unknown').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('searchbox', { name: 'Search summary' }).fill('mathematics');
  await expect.poll(() => queries.some((q) => q.includes('search=mathematics'))).toBe(true);

  await page
    .getByRole('button', { name: /^Actions for/ })
    .first()
    .click();
  await page.getByRole('menuitem', { name: 'Details' }).click();
  const dialog = page.getByRole('dialog', { name: 'Audit log details' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('GRADE_RULE_UPDATED')).toBeVisible();
  await expect(dialog.getByRole('table', { name: 'Before / After' })).toContainText('[REDACTED]');
  await expectNoHorizontalOverflow(page);
  await expect(page.getByRole('button', { name: /delete|edit/i })).toHaveCount(0);
});

test('the menu links to the audit logs', async ({ page }, testInfo) => {
  await signedIn(page, 'ROLE_ADMIN');
  await page.route('**/api/v1/admin/**', (route) => route.fulfill({ json: pageOf([]) }));
  await answer(page, /\/api\/v1\/admin\/audit-logs\/actions$/, []);
  await answer(page, /\/api\/v1\/admin\/audit-logs\/entity-types$/, []);
  await page.goto('/admin/audit-logs');
  if ((testInfo.project.use.viewport?.width ?? 0) < 900) {
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
  }
  await expect(page.getByRole('link', { name: 'Audit logs', exact: true }).first()).toBeVisible();
});
