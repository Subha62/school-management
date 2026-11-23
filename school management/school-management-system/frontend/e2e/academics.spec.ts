import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf } from './support';

const MOBILE_BREAKPOINT = 900;
const PHONE_BREAKPOINT = 600;

const years = [
  {
    id: 'y1',
    name: '2027-2028',
    startDate: '2027-06-01',
    endDate: '2028-03-31',
    status: 'PLANNED',
    version: 0,
  },
  {
    id: 'y2',
    name: '2026-2027',
    startDate: '2026-06-01',
    endDate: '2027-03-31',
    status: 'ACTIVE',
    version: 2,
  },
  {
    id: 'y3',
    name: '2025-2026',
    startDate: '2025-06-01',
    endDate: '2026-03-31',
    status: 'CLOSED',
    version: 4,
  },
];
const grades = [{ id: 'g1', name: 'Grade 8', displayOrder: 8, active: true, version: 0 }];
const subjects = [
  {
    id: 's1',
    code: 'MATH',
    name: 'Mathematics',
    description: 'Core numeracy and algebra',
    active: true,
    version: 0,
  },
];
const section = {
  id: 'sec1',
  academicYear: { id: 'y2', name: '2026-2027' },
  gradeLevel: { id: 'g1', name: 'Grade 8' },
  name: 'A',
  capacity: 40,
  classTeacher: { id: 't1', name: 'Tara Teacher' },
  active: true,
  version: 0,
};
const assignment = {
  id: 'a1',
  academicYearId: 'y2',
  academicYearName: '2026-2027',
  sectionId: 'sec1',
  sectionName: 'A',
  gradeLevelName: 'Grade 8',
  subject: { id: 's1', code: 'MATH', name: 'Mathematics' },
  teacher: { id: 't1', name: 'Tara Teacher' },
  weeklyPeriods: 6,
  active: true,
  version: 1,
};
const teachers = [{ id: 't1', firstName: 'Tara', lastName: 'Teacher', email: 'tara@school.test' }];

async function mockAcademics(page: Page, options: { manyYears?: boolean } = {}) {
  await mockBackend(page, { role: 'ROLE_ADMIN', signedIn: true });
  const base = '**/api/v1/admin/';
  await page.route(`${base}academic-years**`, (route) => {
    if (route.request().method() === 'POST') {
      return route.fulfill({ status: 201, json: { ...years[0], id: 'new' } });
    }
    const body = pageOf(years);
    return route.fulfill({
      json: options.manyYears ? { ...body, totalElements: 45, totalPages: 3, last: false } : body,
    });
  });
  await page.route(`${base}grade-levels**`, (route) => route.fulfill({ json: pageOf(grades) }));
  await page.route(`${base}subjects**`, (route) => route.fulfill({ json: pageOf(subjects) }));
  await page.route(`${base}sections**`, (route) => route.fulfill({ json: pageOf([section]) }));
  await page.route(`${base}section-subjects**`, (route) =>
    route.fulfill({ json: pageOf([assignment]) }),
  );
  await page.route(`${base}teacher-options**`, (route) =>
    route.fulfill({ json: pageOf(teachers) }),
  );
}

const widthOf = (testInfo: { project: { use: { viewport?: { width: number } | null } } }) =>
  testInfo.project.use.viewport?.width ?? 0;

test('academic years are a table on large screens and cards on small ones', async ({
  page,
}, testInfo) => {
  await mockAcademics(page);
  await page.goto('/admin/academic-years');
  await expect(page.getByRole('heading', { name: 'Academic years' })).toBeVisible();
  const main = page.getByRole('main');

  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await expect(main.getByRole('listitem')).toHaveCount(3);
    await expect(main.getByRole('table')).toHaveCount(0);
  } else {
    await expect(main.getByRole('table')).toBeVisible();
    await expect(main.getByRole('cell', { name: '2026-2027', exact: true })).toBeVisible();
    await expect(main.getByRole('listitem')).toHaveCount(0);
  }
  await expect(main.getByText('Active', { exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('pagination and filters fit at every width', async ({ page }) => {
  await mockAcademics(page, { manyYears: true });
  await page.goto('/admin/academic-years');

  await expect(page.getByRole('button', { name: /go to next page/i })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: /search by name/i })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('the new-year dialog is full screen on phones, validates and stays inside the viewport', async ({
  page,
}, testInfo) => {
  await mockAcademics(page);
  await page.goto('/admin/academic-years');
  await page.getByRole('button', { name: 'New academic year' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();

  const viewport = page.viewportSize()!;
  const box = (await dialog.boundingBox())!;
  if (widthOf(testInfo) < PHONE_BREAKPOINT) {
    expect(box.width).toBeGreaterThanOrEqual(viewport.width - 1);
    expect(box.height).toBeGreaterThanOrEqual(viewport.height - 1);
  } else {
    expect(box.width).toBeLessThan(viewport.width);
  }

  await dialog.getByLabel(/^name/i).fill('2028-2029');
  await dialog.getByLabel(/start date/i).fill('2028-06-01');
  await dialog.getByLabel(/end date/i).fill('2028-05-01');
  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(dialog.getByText('End date must be after the start date')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('creating an academic year posts the form data', async ({ page }) => {
  await mockAcademics(page);
  let posted: unknown;
  await page.route('**/api/v1/admin/academic-years', async (route) => {
    if (route.request().method() === 'POST') {
      posted = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: { ...years[0], id: 'new' } });
    }
    return route.fallback();
  });
  await page.goto('/admin/academic-years');
  await page.getByRole('button', { name: 'New academic year' }).click();
  const dialog = page.getByRole('dialog');

  await dialog.getByLabel(/^name/i).fill('2028-2029');
  await dialog.getByLabel(/start date/i).fill('2028-06-01');
  await dialog.getByLabel(/end date/i).fill('2029-03-31');
  await dialog.getByRole('button', { name: 'Create' }).click();

  await expect(page.getByText('Academic year created')).toBeVisible();
  expect(posted).toEqual({ name: '2028-2029', startDate: '2028-06-01', endDate: '2029-03-31' });
});

test('activation asks for confirmation in a dialog with comfortable touch targets', async ({
  page,
}) => {
  await mockAcademics(page);
  await page.goto('/admin/academic-years');

  await page.getByRole('button', { name: 'Actions for 2027-2028' }).click();
  await page.getByRole('menuitem', { name: 'Activate' }).click();
  const dialog = page.getByRole('dialog', { name: /activate academic year/i });
  await expect(dialog).toBeVisible();

  const viewport = page.viewportSize()!;
  const box = (await dialog.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width);
  const confirm = (await dialog.getByRole('button', { name: 'Activate' }).boundingBox())!;
  expect(confirm.height).toBeGreaterThanOrEqual(44);
});

test('sections page keeps filters and the list inside the viewport', async ({ page }) => {
  await mockAcademics(page);
  await page.goto('/admin/sections');

  await expect(page.getByRole('heading', { name: 'Sections' })).toBeVisible();
  await expect(page.getByText('Grade 8 - A')).toBeVisible();
  await expect(page.getByText('Tara Teacher')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('grades and subjects pages fit and keep their actions reachable', async ({ page }) => {
  await mockAcademics(page);

  await page.goto('/admin/grades');
  await expect(page.getByRole('button', { name: 'Actions for Grade 8' })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/admin/subjects');
  await expect(page.getByRole('button', { name: 'Actions for Mathematics' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('assigning a subject: pick a section, open the dialog and search for a teacher', async ({
  page,
}) => {
  await mockAcademics(page);
  await page.goto('/admin/teacher-assignments');
  const assign = page.getByRole('button', { name: 'Assign subject' });
  await expect(assign).toBeDisabled();

  await page.getByRole('combobox', { name: /^section/i }).click();
  await page.getByRole('option', { name: 'Grade 8 - A' }).click();
  await expect(assign).toBeEnabled();
  await assign.click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Section')).toHaveValue('Grade 8 - A');
  await dialog.getByRole('combobox', { name: /teacher/i }).fill('Tara');
  await expect(page.getByRole('option', { name: /Tara Teacher/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('admin navigation reaches the academic pages', async ({ page }, testInfo) => {
  await mockAcademics(page);
  await page.goto('/admin');

  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
  }
  await page.getByRole('link', { name: 'Subjects' }).click();

  await expect(page).toHaveURL(/\/admin\/subjects$/);
  await expect(page.getByRole('heading', { name: 'Subjects' })).toBeVisible();
  if (widthOf(testInfo) < MOBILE_BREAKPOINT) {
    await expect(page.getByRole('navigation')).toHaveCount(0);
  }
});
