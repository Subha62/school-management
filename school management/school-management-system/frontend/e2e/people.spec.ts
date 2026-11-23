import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf } from './support';

const MOBILE_BREAKPOINT = 900;
const PHONE_BREAKPOINT = 600;

const widthOf = (testInfo: { project: { use: { viewport?: { width: number } | null } } }) =>
  testInfo.project.use.viewport?.width ?? 0;

const year1 = {
  id: 'y1',
  name: '2026-2027',
  startDate: '2026-06-01',
  endDate: '2027-03-31',
  status: 'ACTIVE',
  version: 1,
};
const year2 = {
  id: 'y2',
  name: '2027-2028',
  startDate: '2027-06-01',
  endDate: '2028-03-31',
  status: 'PLANNED',
  version: 0,
};
const grades = [
  { id: 'g8', name: 'Grade 8', displayOrder: 8, active: true, version: 0 },
  { id: 'g9', name: 'Grade 9', displayOrder: 9, active: true, version: 0 },
];
const sectionOf = (id: string, yearRef: { id: string; name: string }, grade: number) => ({
  id,
  academicYear: yearRef,
  gradeLevel: { id: `g${grade}`, name: `Grade ${grade}` },
  name: 'A',
  capacity: 40,
  classTeacher: null,
  active: true,
  version: 0,
});

const student = {
  id: 'st1',
  userId: 'u-st1',
  admissionNumber: 'S-100',
  firstName: 'Sam',
  lastName: 'Student',
  email: 'sam.student.with.a.rather.long.address@school.example',
  phoneNumber: null,
  dateOfBirth: '2012-04-10',
  gender: 'MALE',
  admissionDate: '2026-06-01',
  bloodGroup: 'O+',
  addressLine1: '1 Main Road',
  addressLine2: null,
  city: 'Pune',
  state: null,
  postalCode: null,
  emergencyContactName: 'Parent',
  emergencyContactPhone: '+91 98765 00000',
  status: 'ACTIVE',
  currentEnrollment: {
    enrollmentId: 'en1',
    academicYear: '2026-2027',
    grade: 'Grade 8',
    section: 'A',
    rollNumber: 1,
  },
  version: 0,
};
const enrollment = {
  id: 'en1',
  student: { id: 'st1', admissionNumber: 'S-100', name: 'Sam Student' },
  academicYear: { id: 'y1', name: '2026-2027' },
  gradeLevel: { id: 'g8', name: 'Grade 8' },
  section: { id: 'sec8', name: 'A' },
  rollNumber: 1,
  enrollmentDate: '2026-06-01',
  status: 'ACTIVE',
  sourceEnrollmentId: null,
  promotionBatchId: null,
  version: 0,
};
const guardianLink = {
  id: 'l1',
  guardianId: 'gd1',
  firstName: 'Maya',
  lastName: 'Parent',
  relationship: 'MOTHER',
  phoneNumber: '+91 90000 11111',
  email: 'maya.parent@mail.example',
  primaryGuardian: true,
  receivesNotifications: true,
  authorizedPickup: false,
  version: 0,
};
const adminUsers = [
  {
    id: 'u-1',
    username: 'T-0001',
    email: 'tara@school.test',
    firstName: 'Tara',
    lastName: 'Teacher',
    phoneNumber: null,
    roles: ['ROLE_ADMIN'],
    accountStatus: 'ACTIVE',
    temporarilyLocked: false,
    lastLoginAt: null,
    version: 0,
  },
  {
    id: 'u-2',
    username: 'S-100',
    email: 'sam@school.test',
    firstName: 'Sam',
    lastName: 'Student',
    phoneNumber: null,
    roles: ['ROLE_STUDENT'],
    accountStatus: 'SUSPENDED',
    temporarilyLocked: false,
    lastLoginAt: null,
    version: 2,
  },
];

async function mockPeople(page: Page) {
  await mockBackend(page, { role: 'ROLE_ADMIN', signedIn: true });
  const base = '**/api/v1/admin/';
  await page.route(`${base}academic-years**`, (route) =>
    route.fulfill({ json: pageOf([year1, year2]) }),
  );
  await page.route(`${base}grade-levels**`, (route) => route.fulfill({ json: pageOf(grades) }));
  await page.route(`${base}sections**`, (route) => {
    const forYear2 = route.request().url().includes('academicYearId=y2');
    return route.fulfill({
      json: pageOf(
        forYear2
          ? [sectionOf('sec9', { id: 'y2', name: '2027-2028' }, 9)]
          : [sectionOf('sec8', { id: 'y1', name: '2026-2027' }, 8)],
      ),
    });
  });
  await page.route(`${base}teacher-options**`, (route) => route.fulfill({ json: pageOf([]) }));
  await page.route(`${base}students**`, (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/enrollments')) return route.fulfill({ json: [enrollment] });
    if (path.endsWith('/guardians')) return route.fulfill({ json: [guardianLink] });
    if (path.endsWith('/students/st1')) return route.fulfill({ json: student });
    if (route.request().method() === 'POST') return route.fulfill({ status: 201, json: student });
    return route.fulfill({ json: pageOf([student]) });
  });
  await page.route(`${base}enrollments**`, (route) =>
    route.fulfill({ json: pageOf([enrollment]) }),
  );
  await page.route(`${base}users**`, (route) => route.fulfill({ json: pageOf(adminUsers) }));
  await page.route(`${base}promotions**`, (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({
        status: 201,
        json: {
          batchId: 'b1',
          sourceAcademicYear: '2026-2027',
          targetAcademicYear: '2027-2028',
          performedBy: 'Tara Teacher',
          performedAt: '2027-05-01T10:00:00Z',
          total: 1,
          promoted: 1,
          failed: 0,
          results: [],
        },
      });
    }
    return route.fulfill({ json: pageOf([]) });
  });
}

test('students are a table on large screens and cards on small ones, without overflow', async ({
  page,
}, testInfo) => {
  await mockPeople(page);
  await page.goto('/admin/students');
  await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible();
  const main = page.getByRole('main');

  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await expect(main.getByRole('listitem')).toHaveCount(1);
    await expect(main.getByRole('table')).toHaveCount(0);
  } else {
    await expect(main.getByRole('table')).toBeVisible();
  }
  await expect(main.getByRole('link', { name: 'Sam Student' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the student page shows guardians and history without horizontal overflow', async ({
  page,
}) => {
  await mockPeople(page);
  await page.goto('/admin/students');
  await page.getByRole('link', { name: 'Sam Student' }).click();

  await expect(page.getByRole('heading', { name: 'Sam Student' })).toBeVisible();
  await expect(page.getByText('Maya Parent (Mother)')).toBeVisible();
  await expect(page.getByRole('table', { name: 'Enrollment history' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the new-student dialog fits the screen and validates required fields', async ({
  page,
}, testInfo) => {
  await mockPeople(page);
  await page.goto('/admin/students');
  await page.getByRole('button', { name: 'New student' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();

  const viewport = page.viewportSize()!;
  const box = (await dialog.boundingBox())!;
  if (widthOf(testInfo) < PHONE_BREAKPOINT) {
    expect(box.width).toBeGreaterThanOrEqual(viewport.width - 1);
  } else {
    expect(box.width).toBeLessThan(viewport.width);
  }

  await dialog.getByRole('button', { name: 'Generate' }).click();
  await expect(dialog.getByLabel(/^initial password/i)).toHaveAttribute('type', 'text');
  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(dialog.getByText('Admission number is required')).toBeVisible();
  await expect(dialog.getByText('First name is required')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('promotion lists students with a suggested target and posts the selection', async ({
  page,
}) => {
  await mockPeople(page);
  let posted: unknown;
  await page.route('**/api/v1/admin/promotions', (route) => {
    if (route.request().method() === 'POST') {
      posted = route.request().postDataJSON();
      return route.fulfill({
        status: 201,
        json: {
          batchId: 'b1',
          sourceAcademicYear: '2026-2027',
          targetAcademicYear: '2027-2028',
          performedBy: 'Tara Teacher',
          performedAt: '2027-05-01T10:00:00Z',
          total: 1,
          promoted: 1,
          failed: 0,
          results: [],
        },
      });
    }
    return route.fallback();
  });
  await page.goto('/admin/promotion');

  await expect(page.getByRole('list', { name: 'Students to promote' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.getByRole('checkbox', { name: 'Select Sam Student' }).check();
  await page.getByRole('button', { name: 'Promote 1 selected' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Promote' }).click();

  await expect(page.getByText('1 of 1 students promoted to 2027-2028')).toBeVisible();
  expect(posted).toEqual({
    sourceAcademicYearId: 'y1',
    targetAcademicYearId: 'y2',
    items: [{ studentId: 'st1', targetSectionId: 'sec9' }],
  });
  await expectNoHorizontalOverflow(page);
});

test('users and enrollments fit every width', async ({ page }) => {
  await mockPeople(page);
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible();
  await expect(page.getByText('Suspended', { exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/admin/enrollments');
  await expect(page.getByRole('heading', { name: 'Enrollments' })).toBeVisible();
  await expect(page.getByText('Sam Student (S-100)')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the people pages are reachable from the navigation', async ({ page }, testInfo) => {
  await mockPeople(page);
  await page.goto('/admin');
  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
  }
  await page.getByRole('link', { name: 'Teachers' }).click();
  await expect(page).toHaveURL(/\/admin\/teachers$/);
});
