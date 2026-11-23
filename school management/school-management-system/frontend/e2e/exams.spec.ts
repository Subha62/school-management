import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, pageOf, type AuthorityName } from './support';

const longName = 'Bartholomew Maximilian Featherstonehaugh-Cholmondeley Pupil';

const subject = (id: string, code: string, name: string) => ({
  id,
  subjectId: `s-${id}`,
  subjectCode: code,
  subjectName: name,
  maxMarks: 100,
  passMarks: 35,
  examDate: null,
});

const exam = (status: string) => ({
  id: 'exam-1',
  academicYearId: 'y1',
  academicYearName: '2026-2027',
  gradeLevelId: 'g1',
  gradeLevelName: 'Grade 8',
  name: 'Term 1 examination with a fairly long name',
  startDate: null,
  endDate: null,
  status,
  subjects: [subject('1', 'MATH', 'Mathematics'), subject('2', 'ENG', 'English')],
  lockedAt: null,
  publishedAt: null,
  version: 0,
});

const result = {
  studentId: 'st1',
  admissionNumber: 'S-1',
  studentName: longName,
  sectionId: 'sec1',
  sectionName: 'Grade 8 - A',
  rollNumber: 1,
  subjects: [
    {
      subjectId: 's-1',
      subjectCode: 'MATH',
      subjectName: 'Mathematics',
      maxMarks: 100,
      passMarks: 35,
      marks: 90,
      absent: false,
      percentage: 90,
      grade: 'A',
      status: 'PASS',
      remarks: 'Excellent work this term, keep it up',
    },
    {
      subjectId: 's-2',
      subjectCode: 'ENG',
      subjectName: 'English',
      maxMarks: 100,
      passMarks: 35,
      marks: null,
      absent: true,
      percentage: 0,
      grade: null,
      status: 'ABSENT',
      remarks: null,
    },
  ],
  totalMarks: 90,
  totalMax: 200,
  percentage: 45,
  grade: 'D',
  result: 'FAIL',
  comment: 'A promising term overall.',
};

const reportCard = {
  examId: 'exam-1',
  examName: 'Term 1 examination with a fairly long name',
  schoolName: 'Demo School',
  academicYearName: '2026-2027',
  gradeLevelName: 'Grade 8',
  startDate: null,
  endDate: null,
  publishedAt: '2026-11-18T10:00:00Z',
  result,
};

/** Answers one API path (matched on the path alone, so query strings do not matter). */
async function answer(page: Page, path: RegExp, body: unknown) {
  await page.route(
    (url) => path.test(url.pathname),
    (route) => route.fulfill({ json: body }),
  );
}

async function academics(page: Page, role: AuthorityName) {
  await mockBackend(page, { role, signedIn: true });
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
  await answer(
    page,
    /\/api\/v1\/admin\/subjects$/,
    pageOf([
      { id: 's-1', code: 'MATH', name: 'Mathematics', description: null, active: true, version: 0 },
    ]),
  );
  await answer(
    page,
    /\/api\/v1\/admin\/sections$/,
    pageOf([
      {
        id: 'sec1',
        academicYear: { id: 'y1', name: '2026-2027' },
        gradeLevel: { id: 'g1', name: 'Grade 8' },
        name: 'A',
        capacity: 40,
        classTeacher: null,
        active: true,
        version: 0,
      },
    ]),
  );
}

test('an administrator sees the grade scale and its coverage', async ({ page }) => {
  await academics(page, 'ROLE_ADMIN');
  await answer(
    page,
    /\/api\/v1\/admin\/grade-rules$/,
    pageOf([
      {
        id: 'r1',
        grade: 'A',
        description: 'Excellent',
        minPercent: 50,
        maxPercent: 100,
        version: 0,
      },
      {
        id: 'r2',
        grade: 'F',
        description: 'Needs support with a long description to test wrapping',
        minPercent: 0,
        maxPercent: 49.99,
        version: 0,
      },
    ]),
  );
  await page.goto('/admin/grade-rules');
  await expect(page.getByRole('heading', { name: 'Grade rules' })).toBeVisible();
  await expect(page.getByText(/covers every percentage from 0% to 100%/)).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator sees exams, then one exam with its progress and lifecycle buttons', async ({
  page,
}) => {
  await academics(page, 'ROLE_ADMIN');
  await answer(page, /\/api\/v1\/admin\/exams$/, pageOf([exam('OPEN_FOR_MARKS')]));
  await answer(page, /\/api\/v1\/admin\/exams\/exam-1$/, exam('OPEN_FOR_MARKS'));
  await answer(page, /\/api\/v1\/admin\/exams\/exam-1\/progress$/, {
    examId: 'exam-1',
    status: 'OPEN_FOR_MARKS',
    complete: false,
    missing: 2,
    rows: [
      {
        examSubjectId: '1',
        subjectName: 'Mathematics',
        sectionId: 'sec1',
        sectionName: 'Grade 8 - A',
        teacherName: longName,
        expected: 3,
        entered: 1,
      },
    ],
  });

  await page.goto('/admin/exams');
  await expect(page.getByRole('heading', { name: 'Exams', exact: true })).toBeVisible();
  await expect(page.getByText(/Term 1 examination with a fairly long name/).first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/admin/exams/exam-1');
  await expect(page.getByRole('button', { name: 'Lock marks' })).toBeVisible();
  await expect(page.getByText('2 missing')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('an administrator previews the results of a locked exam', async ({ page }) => {
  await academics(page, 'ROLE_ADMIN');
  await answer(page, /\/api\/v1\/admin\/exams$/, pageOf([exam('LOCKED')]));
  await answer(page, /\/api\/v1\/admin\/exams\/exam-1\/results$/, {
    examId: 'exam-1',
    examName: 'Term 1',
    status: 'LOCKED',
    preview: true,
    sectionId: 'sec1',
    results: [result],
  });
  await page.goto('/admin/results?examId=exam-1&sectionId=sec1');
  await expect(page.getByText(/Preview - not published/)).toBeVisible();
  await expect(page.getByText(longName).first()).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a teacher lists exams and enters marks with long names at every width', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_TEACHER', signedIn: true });
  await answer(
    page,
    /\/api\/v1\/teacher\/exams$/,
    pageOf([
      {
        id: 'exam-1',
        name: 'Term 1 examination with a fairly long name',
        status: 'OPEN_FOR_MARKS',
        academicYearName: '2026-2027',
        gradeLevelName: 'Grade 8',
        startDate: null,
        endDate: null,
        tasks: [
          {
            examSubjectId: 'es-1',
            subjectName: 'Mathematics',
            sectionId: 'sec1',
            sectionName: 'Grade 8 - A',
            maxMarks: 100,
            passMarks: 35,
            expected: 2,
            entered: 0,
          },
        ],
        commentSections: [{ id: 'sec1', name: 'Grade 8 - A' }],
      },
    ]),
  );
  let saved: unknown = null;
  await page.route(
    (url) => /\/api\/v1\/teacher\/exams\/exam-1\/marks$/.test(url.pathname),
    (route) => {
      if (route.request().method() === 'PUT') {
        saved = route.request().postDataJSON();
      }
      return route.fulfill({
        json: {
          examId: 'exam-1',
          examName: 'Term 1',
          status: 'OPEN_FOR_MARKS',
          examSubjectId: 'es-1',
          subjectName: 'Mathematics',
          sectionId: 'sec1',
          sectionName: 'Grade 8 - A',
          maxMarks: 100,
          passMarks: 35,
          editable: true,
          rows: [
            {
              studentId: 'st1',
              admissionNumber: 'S-1',
              studentName: longName,
              rollNumber: 1,
              marks: null,
              absent: false,
              remarks: null,
              entered: false,
            },
            {
              studentId: 'st2',
              admissionNumber: 'S-2',
              studentName: 'Bob Pupil',
              rollNumber: 2,
              marks: null,
              absent: false,
              remarks: null,
              entered: false,
            },
          ],
        },
      });
    },
  );

  await page.goto('/teacher/marks');
  await expect(page.getByRole('heading', { name: 'Exams and marks' })).toBeVisible();
  await expect(page.getByText('0 of 2 entered')).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.getByRole('link', { name: 'Enter marks: Mathematics, Grade 8 - A' }).click();
  await expect(page.getByLabel(`Marks for ${longName}`, { exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.getByLabel(`Marks for ${longName}`, { exact: true }).fill('88.5');
  await page.getByRole('button', { name: 'Save marks' }).click();
  await expect
    .poll(() => saved)
    .toEqual({
      entries: [{ studentId: 'st1', marks: 88.5, absent: false, remarks: null }],
    });
  await expectNoHorizontalOverflow(page);
});

test('a student reads a published report card that fits every screen', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await answer(
    page,
    /\/api\/v1\/student\/results$/,
    pageOf([
      {
        examId: 'exam-1',
        examName: 'Term 1 examination with a fairly long name',
        academicYearName: '2026-2027',
        gradeLevelName: 'Grade 8',
        publishedAt: '2026-11-18T10:00:00Z',
        percentage: 45,
        grade: 'D',
        result: 'FAIL',
      },
    ]),
  );
  await answer(page, /\/api\/v1\/student\/results\/exam-1$/, reportCard);

  await page.goto('/student/results');
  await expect(page.getByRole('heading', { name: 'Exams and results' })).toBeVisible();
  await expect(page.getByText('45%').first()).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/student/report-cards/exam-1');
  await expect(page.getByLabel(`Report card of ${longName}`)).toBeVisible();
  await expect(page.getByText('A promising term overall.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('printing shows only the report card', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await answer(page, /\/api\/v1\/student\/results\/exam-1$/, reportCard);
  await page.goto('/student/report-cards/exam-1');
  await expect(page.getByLabel(`Report card of ${longName}`)).toBeVisible();
  await page.emulateMedia({ media: 'print' });
  await expect(page.getByLabel(`Report card of ${longName}`)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Print' })).toBeHidden();
  await expect(page.locator('.MuiAppBar-root')).toBeHidden();
});

test('the menus link to the new pages', async ({ page }, testInfo) => {
  await mockBackend(page, { role: 'ROLE_ADMIN', signedIn: true });
  await page.route('**/api/v1/admin/**', (route) => route.fulfill({ json: pageOf([]) }));
  await page.goto('/admin');
  if ((testInfo.project.use.viewport?.width ?? 0) < 900) {
    await page.getByRole('button', { name: 'Open navigation menu' }).click();
  }
  for (const name of ['Exams', 'Grade rules', 'Results']) {
    await expect(page.getByRole('link', { name, exact: true }).first()).toBeVisible();
  }
});
