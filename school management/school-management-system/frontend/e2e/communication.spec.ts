import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf, type AuthorityName } from './support';

const longTitle =
  'Annual sports day, the class photograph and the very long reminder about uniforms and shoes';
const longName = 'Bartholomew Maximilian Featherstonehaugh-Cholmondeley Pupil';

async function answer(page: Page, path: RegExp, body: unknown) {
  await page.route(
    (url) => path.test(url.pathname),
    (route) => route.fulfill({ json: body }),
  );
}

async function signedIn(page: Page, role: AuthorityName, unread = 2) {
  await mockBackend(page, { role, signedIn: true });
  await answer(page, /\/api\/v1\/announcements\/unread-count$/, { count: unread });
}

const announcement = (overrides: Record<string, unknown> = {}) => ({
  id: 'an-1',
  title: longTitle,
  body: 'Please read this carefully.\n\nBring water and a hat. <b>not bold</b>',
  audience: 'ALL',
  sectionId: null,
  sectionName: null,
  status: 'PUBLISHED',
  publishAt: null,
  expiresAt: null,
  publishedAt: '2026-11-18T10:00:00Z',
  authorName: 'Asha Admin',
  createdAt: '2026-11-18T09:00:00Z',
  readCount: null,
  read: false,
  live: true,
  version: 0,
  ...overrides,
});

const fee = (overrides: Record<string, unknown> = {}) => ({
  id: 'fee-1',
  studentId: 'st-1',
  admissionNumber: 'S-0001',
  studentName: longName,
  className: 'Grade 8 - A',
  academicYearId: 'y1',
  academicYearName: '2026-2027',
  structureId: 'fs-1',
  categoryName: 'Tuition',
  description: 'Term 1 tuition',
  amount: 15000,
  paidAmount: 5000,
  balance: 10000,
  dueDate: '2026-12-10',
  status: 'PARTIALLY_PAID',
  waiveReason: null,
  waivedAt: null,
  version: 0,
  ...overrides,
});

const payment = (overrides: Record<string, unknown> = {}) => ({
  id: 'pay-1',
  receiptNumber: 'REC-2026-000001',
  studentFeeId: 'fee-1',
  studentId: 'st-1',
  admissionNumber: 'S-0001',
  studentName: longName,
  categoryName: 'Tuition',
  amount: 5000,
  method: 'UPI',
  reference: 'UPI-DEMO-0001',
  paidOn: '2026-11-18',
  receivedByName: 'Asha Admin',
  createdAt: '2026-11-18T10:00:00Z',
  reversed: false,
  reversalReason: null,
  reversedAt: null,
  reversedByName: null,
  ...overrides,
});

const receipt = (reversed = false) => ({
  receiptNumber: 'REC-2026-000001',
  schoolName: 'Demo School',
  studentName: longName,
  admissionNumber: 'S-0001',
  className: 'Grade 8 - A',
  academicYearName: '2026-2027',
  categoryName: 'Tuition',
  description: 'Term 1 tuition',
  feeAmount: 15000,
  amount: 5000,
  method: 'UPI',
  reference: 'UPI-DEMO-0001',
  paidOn: '2026-11-18',
  receivedByName: 'Asha Admin',
  balance: 10000,
  reversed,
  reversalReason: reversed ? 'Entered for the wrong student' : null,
  reversedAt: reversed ? '2026-11-19T10:00:00Z' : null,
});

async function academics(page: Page) {
  await answer(
    page,
    /\/api\/v1\/admin\/academic-years$/,
    pageOf([
      {
        id: 'y1',
        name: '2026-2027',
        startDate: '2026-06-01',
        endDate: '2027-03-31',
        status: 'ACTIVE',
        version: 0,
      },
    ]),
  );
  await answer(
    page,
    /\/api\/v1\/admin\/grade-levels$/,
    pageOf([{ id: 'g1', name: 'Grade 8', displayOrder: 8, active: true, version: 0 }]),
  );
  await answer(page, /\/api\/v1\/admin\/sections$/, pageOf([]));
}

test('a student reads announcements, with unread ones marked and no way to write', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_STUDENT');
  await answer(
    page,
    /\/api\/v1\/announcements$/,
    pageOf([announcement(), announcement({ id: 'an-2', title: 'Read one', read: true })]),
  );
  await page.goto('/student/announcements');
  await expect(page.getByRole('heading', { name: 'Announcements', exact: true })).toBeVisible();
  await expect(page.getByText('Unread', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Manage' })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('opening an announcement marks it read and shows its message as plain text', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_STUDENT');
  await answer(page, /\/api\/v1\/announcements$/, pageOf([announcement()]));
  let marked = 0;
  await page.route(
    (url) => /\/api\/v1\/announcements\/an-1\/read$/.test(url.pathname),
    (route) => {
      marked += 1;
      return route.fulfill({ status: 204 });
    },
  );
  await page.goto('/student/announcements');
  await page.getByText(longTitle).first().click();
  await expect(page.getByText(/Bring water and a hat\. <b>not bold<\/b>/)).toBeVisible();
  await expect.poll(() => marked).toBe(1);
  await expectNoHorizontalOverflow(page);
});

test('an administrator manages announcements', async ({ page }) => {
  await signedIn(page, 'ROLE_ADMIN');
  await academics(page);
  await answer(page, /\/api\/v1\/announcements$/, pageOf([announcement()]));
  await answer(
    page,
    /\/api\/v1\/admin\/announcements$/,
    pageOf([
      announcement({ readCount: 12 }),
      announcement({
        id: 'an-3',
        title: 'Draft notice',
        status: 'DRAFT',
        live: false,
        publishedAt: null,
      }),
    ]),
  );
  await page.goto('/admin/announcements');
  await page.getByRole('tab', { name: 'Manage' }).click();
  await expect(page.getByText('Draft notice').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /new announcement/i })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a teacher reads and writes announcements for their classes', async ({ page }) => {
  await signedIn(page, 'ROLE_TEACHER', 0);
  await answer(page, /\/api\/v1\/announcements$/, pageOf([]));
  await answer(
    page,
    /\/api\/v1\/teacher\/announcements$/,
    pageOf([announcement({ audience: 'SECTION', sectionName: 'Grade 8 - A' })]),
  );
  await answer(page, /\/api\/v1\/teacher\/attendance\/classes$/, [
    {
      sectionId: 'sec1',
      sectionName: 'Grade 8 - A',
      academicYear: '2026-2027',
      daily: true,
      subjects: [],
    },
  ]);
  await page.goto('/teacher/announcements');
  await expect(page.getByRole('heading', { name: 'Announcements', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Manage' }).click();
  await expect(page.getByText(longTitle).first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator sees fee categories and structures', async ({ page }) => {
  await signedIn(page, 'ROLE_ADMIN');
  await academics(page);
  await answer(
    page,
    /\/api\/v1\/admin\/fee-categories$/,
    pageOf([
      {
        id: 'c1',
        name: 'Tuition',
        description: 'Term tuition with a rather long description to test wrapping',
        active: true,
        version: 0,
      },
    ]),
  );
  await answer(
    page,
    /\/api\/v1\/admin\/fee-structures$/,
    pageOf([
      {
        id: 'fs-1',
        academicYearId: 'y1',
        academicYearName: '2026-2027',
        gradeLevelId: 'g1',
        gradeLevelName: 'Grade 8',
        feeCategoryId: 'c1',
        categoryName: 'Tuition',
        description: 'Term 1 tuition',
        amount: 15000,
        dueDate: '2026-12-10',
        active: true,
        billedStudents: 24,
        version: 0,
      },
    ]),
  );
  await page.goto('/admin/fee-categories');
  await expect(page.getByRole('heading', { name: 'Fee categories' })).toBeVisible();
  await expect(page.getByText('Tuition').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.goto('/admin/fee-structures');
  await expect(page.getByRole('heading', { name: 'Fee structures' })).toBeVisible();
  await expect(page.getByText(/15,000/).first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator sees student fees with totals, then one bill with its payments', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_ADMIN');
  await academics(page);
  await answer(page, /\/api\/v1\/admin\/fee-reports\/summary$/, {
    academicYearId: 'y1',
    gradeLevelId: null,
    billed: 30000,
    collected: 5000,
    outstanding: 25000,
    overdue: 3000,
    waived: 0,
    countByStatus: { NOT_DUE: 0, DUE: 1, PARTIALLY_PAID: 1, PAID: 0, OVERDUE: 1, WAIVED: 0 },
    byCategory: [{ categoryName: 'Tuition', billed: 30000, collected: 5000, outstanding: 25000 }],
  });
  await answer(
    page,
    /\/api\/v1\/admin\/student-fees$/,
    pageOf([fee(), fee({ id: 'fee-2', status: 'OVERDUE', paidAmount: 0, balance: 15000 })]),
  );
  await answer(page, /\/api\/v1\/admin\/student-fees\/fee-1$/, {
    fee: fee(),
    payments: [
      payment(),
      payment({
        id: 'pay-2',
        receiptNumber: 'REC-2026-000002',
        reversed: true,
        reversalReason: 'Wrong student',
      }),
    ],
  });
  await page.goto('/admin/student-fees');
  await expect(page.getByRole('heading', { name: 'Student fees' })).toBeVisible();
  await expect(page.getByText(longName).first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/admin/student-fees/fee-1');
  await expect(page.getByText('REC-2026-000001').first()).toBeVisible();
  await expect(page.getByText('Reversed').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator records a payment and the request carries the amount as a number', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_ADMIN');
  await academics(page);
  await answer(page, /\/api\/v1\/admin\/fee-reports\/summary$/, {
    academicYearId: 'y1',
    gradeLevelId: null,
    billed: 15000,
    collected: 5000,
    outstanding: 10000,
    overdue: 0,
    waived: 0,
    countByStatus: { NOT_DUE: 0, DUE: 0, PARTIALLY_PAID: 1, PAID: 0, OVERDUE: 0, WAIVED: 0 },
    byCategory: [],
  });
  await answer(page, /\/api\/v1\/admin\/student-fees$/, pageOf([fee()]));
  await answer(page, /\/api\/v1\/admin\/student-fees\/fee-1$/, {
    fee: fee(),
    payments: [payment()],
  });
  let sent: unknown = null;
  await page.route(
    (url) => /\/api\/v1\/admin\/student-fees\/fee-1\/payments$/.test(url.pathname),
    (route) => {
      sent = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: payment({ id: 'pay-9', amount: 250.5 }) });
    },
  );
  await page.goto('/admin/student-fees/fee-1');
  await page.getByRole('button', { name: 'Record payment' }).first().click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Amount').fill('250.50');
  await dialog.getByRole('button', { name: 'Record payment' }).click();
  await expect.poll(() => sent).toMatchObject({ amount: 250.5, method: 'CASH' });
});

test('an administrator sees payments with the money received', async ({ page }) => {
  await signedIn(page, 'ROLE_ADMIN');
  await academics(page);
  await answer(page, /\/api\/v1\/admin\/fee-reports\/collections$/, {
    from: '2026-11-01',
    to: '2026-11-30',
    payments: 2,
    received: 5300,
    reversed: 300,
    net: 5000,
    byMethod: [{ method: 'UPI', count: 1, total: 5000 }],
  });
  await answer(
    page,
    /\/api\/v1\/admin\/payments$/,
    pageOf([
      payment(),
      payment({
        id: 'pay-2',
        receiptNumber: 'REC-2026-000002',
        amount: 300,
        reversed: true,
        reversalReason: 'Wrong',
      }),
    ]),
  );
  await answer(page, /\/api\/v1\/admin\/payments\/pay-1\/receipt$/, receipt());
  await page.goto('/admin/payments');
  await expect(page.getByRole('heading', { name: 'Payments', exact: true })).toBeVisible();
  await expect(page.getByText('REC-2026-000001').first()).toBeVisible();
  await expect(page.getByText('Net collected')).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.goto('/admin/payments/pay-1/receipt');
  await expect(page.getByLabel('Receipt REC-2026-000001')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a student sees their fees and a printable receipt, with reversed payments marked', async ({
  page,
}) => {
  await signedIn(page, 'ROLE_STUDENT');
  await answer(
    page,
    /\/api\/v1\/student\/fees$/,
    pageOf([
      fee({ studentName: 'Sam Student' }),
      fee({
        id: 'fee-2',
        categoryName: 'Transport',
        status: 'OVERDUE',
        paidAmount: 0,
        balance: 3000,
        amount: 3000,
      }),
    ]),
  );
  await answer(page, /\/api\/v1\/student\/fees\/fee-1$/, { fee: fee(), payments: [payment()] });
  await answer(page, /\/api\/v1\/student\/payments\/pay-1\/receipt$/, receipt(true));

  await page.goto('/student/fees');
  await expect(page.getByRole('heading', { name: 'Fees', exact: true })).toBeVisible();
  await expect(page.getByText('Overdue').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/student/fees/fee-1');
  await expect(page.getByText('REC-2026-000001').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/student/fees/fee-1/receipts/pay-1');
  await expect(page.getByLabel('Receipt REC-2026-000001')).toBeVisible();
  await expect(
    page.getByRole('alert').filter({ hasText: 'Entered for the wrong student' }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.emulateMedia({ media: 'print' });
  await expect(page.getByRole('button', { name: 'Print' })).toBeHidden();
});

test('the menus link to the Phase 8 pages and the unread count shows on Announcements', async ({
  page,
}, testInfo) => {
  await signedIn(page, 'ROLE_ADMIN', 3);
  await page.route('**/api/v1/admin/**', (route) => route.fulfill({ json: pageOf([]) }));
  await page.goto('/admin');
  if ((testInfo.project.use.viewport?.width ?? 0) < 900) {
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
  }
  for (const name of ['Fee categories', 'Fee structures', 'Student fees', 'Payments']) {
    await expect(page.getByRole('link', { name, exact: true }).first()).toBeVisible();
  }
  await expect(page.getByRole('link', { name: /Announcements.*3 unread/ }).first()).toBeVisible();
});
