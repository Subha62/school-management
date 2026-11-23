import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf } from './support';

const FUTURE = '2099-01-02T09:30:00Z';
const PAST = '2026-11-01T09:30:00Z';
const longName =
  'Quarterly-revision-worksheet-with-an-extremely-long-file-name-for-layout-testing.pdf';
const attachment = {
  id: 'f1',
  name: longName,
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedAt: PAST,
};

const assignment = (status: string) => ({
  id: 'as1',
  sectionId: 'sec1',
  sectionName: 'Grade 8 - A',
  academicYearName: '2026-2027',
  subjectId: 'sub1',
  subjectName: 'Mathematics',
  teacherId: 't1',
  teacherName: 'Tara Teacher',
  title: 'Worksheet 1 with a reasonably long title to check wrapping on phones',
  description: 'Do every question.\n\nShow your working.',
  dueAt: FUTURE,
  maxMarks: 10,
  allowLate: false,
  status,
  publishedAt: PAST,
  closedAt: null,
  attachments: [attachment],
  counts: { enrolled: 2, submitted: 1, awaitingReview: 1, reviewed: 0 },
  version: 0,
});

const submission = (overrides: object = {}) => ({
  id: 'sb1',
  assignmentId: 'as1',
  studentId: 'st1',
  admissionNumber: 'S-0001',
  studentName: 'Sam Student',
  status: 'SUBMITTED',
  textAnswer: 'My answer',
  submittedAt: PAST,
  marks: null,
  feedback: null,
  reviewedAt: null,
  reviewedBy: null,
  attachments: [attachment],
  version: 1,
  ...overrides,
});

const studentView = (mine: object | null, canSubmit = true) => {
  const view: Record<string, unknown> = { ...assignment('PUBLISHED'), submission: mine, canSubmit };
  delete view.counts;
  delete view.teacherId;
  return view;
};

async function teacherBackend(page: Page) {
  await mockBackend(page, { role: 'ROLE_TEACHER', signedIn: true });
  await page.route('**/api/v1/teacher/attendance/classes**', (route) =>
    route.fulfill({
      json: [
        {
          sectionId: 'sec1',
          sectionName: 'Grade 8 - A',
          academicYear: '2026-2027',
          daily: true,
          subjects: [{ id: 'sub1', name: 'Mathematics' }],
        },
      ],
    }),
  );
  await page.route('**/api/v1/teacher/assignments?**', (route) =>
    route.fulfill({ json: pageOf([assignment('PUBLISHED')]) }),
  );
  await page.route('**/api/v1/teacher/assignments/as1', (route) =>
    route.fulfill({ json: assignment('PUBLISHED') }),
  );
  await page.route('**/api/v1/teacher/assignments/as1/submissions', (route) =>
    route.fulfill({
      json: [
        {
          studentId: 'st1',
          admissionNumber: 'S-0001',
          studentName: 'Sam Student',
          rollNumber: 1,
          submissionId: 'sb1',
          status: 'SUBMITTED',
          submittedAt: PAST,
          marks: null,
        },
        {
          studentId: 'st2',
          admissionNumber: 'S-0002',
          studentName: 'Ann Pupil',
          rollNumber: 2,
          submissionId: null,
          status: null,
          submittedAt: null,
          marks: null,
        },
      ],
    }),
  );
}

test('a teacher sees their assignments without horizontal overflow', async ({ page }) => {
  await teacherBackend(page);
  await page.goto('/teacher/assignments');
  await expect(page.getByRole('heading', { name: 'Assignments' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Worksheet 1 with a reasonably/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a teacher reviews a submission from the assignment page', async ({ page }) => {
  await teacherBackend(page);
  await page.route('**/api/v1/teacher/assignments/as1/submissions/sb1', (route) =>
    route.fulfill({ json: submission() }),
  );
  let reviewBody: unknown = null;
  await page.route('**/api/v1/teacher/assignments/as1/submissions/sb1/review', (route) => {
    reviewBody = route.request().postDataJSON();
    return route.fulfill({ json: submission({ status: 'REVIEWED', marks: 9 }) });
  });

  await page.goto('/teacher/assignments/as1');
  await expect(page.getByText('Ann Pupil').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('button', { name: 'Review Sam Student' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('My answer')).toBeVisible();
  await dialog.getByLabel(/^Marks/).fill('9');
  await dialog.getByLabel('Feedback').fill('Good work');
  await expectNoHorizontalOverflow(page);
  await dialog.getByRole('button', { name: 'Save review' }).click();

  await expect(dialog).toBeHidden();
  expect(reviewBody).toEqual({
    outcome: 'REVIEWED',
    marks: 9,
    feedback: 'Good work',
    version: 1,
  });
});

test('a student reads an assignment and hands in an answer with a file', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  let handedIn = false;
  await page.route('**/api/v1/student/assignments/as1', (route) =>
    route.fulfill({ json: studentView(handedIn ? submission() : null) }),
  );
  let contentType = '';
  await page.route('**/api/v1/student/assignments/as1/submission', (route) => {
    contentType = route.request().headers()['content-type'] ?? '';
    handedIn = true;
    return route.fulfill({ json: studentView(submission()) });
  });

  await page.goto('/student/assignments/as1');
  await expect(page.getByRole('heading', { name: /Worksheet 1 with a reasonably/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('textbox', { name: 'Your answer' }).fill('My answer');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'work.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4 test'),
  });
  await expect(page.getByText(/work\.pdf/)).toBeVisible();
  await page.getByRole('button', { name: 'Submit' }).click();

  await expect(page.getByRole('button', { name: 'Update submission' })).toBeVisible();
  expect(contentType).toContain('multipart/form-data');
  await expectNoHorizontalOverflow(page);
});

test('a student is told a file of the wrong type is refused', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.route('**/api/v1/student/assignments/as1', (route) =>
    route.fulfill({ json: studentView(null) }),
  );
  await page.goto('/student/assignments/as1');
  await page.locator('input[type="file"]').setInputFiles({
    name: 'run.exe',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from('MZ'),
  });
  await expect(page.getByText(/"run.exe" is not an allowed type/)).toBeVisible();
});

test('a student sees marks and feedback once the work is reviewed', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.route('**/api/v1/student/assignments/as1', (route) =>
    route.fulfill({
      json: studentView(
        submission({ status: 'REVIEWED', marks: 8.5, feedback: 'Great effort' }),
        false,
      ),
    }),
  );
  await page.goto('/student/assignments/as1');
  await expect(page.getByText(/Marks: 8\.5 \/ 10/)).toContainText('Great effort');
  await expect(page.getByRole('button', { name: 'Update submission' })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('a student lists assignments and submissions', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.route('**/api/v1/student/assignments?**', (route) =>
    route.fulfill({ json: pageOf([studentView(null)]) }),
  );
  await page.route('**/api/v1/student/submissions?**', (route) =>
    route.fulfill({
      json: pageOf([
        {
          id: 'sb1',
          assignmentId: 'as1',
          assignmentTitle: 'Worksheet 1',
          subjectName: 'Mathematics',
          sectionName: 'Grade 8 - A',
          studentId: 'st1',
          admissionNumber: 'S-0001',
          studentName: 'Sam Student',
          status: 'REVIEWED',
          submittedAt: PAST,
          dueAt: FUTURE,
          marks: 8.5,
          maxMarks: 10,
        },
      ]),
    }),
  );

  await page.goto('/student/assignments');
  await expect(page.getByRole('link', { name: /Worksheet 1 with a reasonably/ })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/student/submissions');
  await expect(page.getByText('8.5 / 10')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a teacher sees the review queue', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_TEACHER', signedIn: true });
  await page.route('**/api/v1/teacher/submissions?**', (route) =>
    route.fulfill({
      json: pageOf([
        {
          id: 'sb1',
          assignmentId: 'as1',
          assignmentTitle: 'Worksheet 1',
          subjectName: 'Mathematics',
          sectionName: 'Grade 8 - A',
          studentId: 'st1',
          admissionNumber: 'S-0001',
          studentName: 'Sam Student',
          status: 'LATE',
          submittedAt: PAST,
          dueAt: PAST,
          marks: null,
          maxMarks: 10,
        },
      ]),
    }),
  );
  await page.goto('/teacher/submissions');
  await expect(page.getByText('Sam Student (S-0001)')).toBeVisible();
  await expect(page.getByText('Late').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
