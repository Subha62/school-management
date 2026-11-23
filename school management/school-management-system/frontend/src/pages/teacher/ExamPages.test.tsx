import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import {
  makeExam,
  makeGrade,
  makeGradeRule,
  makeMarksSheet,
  makeReportCard,
  makeSection,
  makeStudentResult,
  makeSubject,
  makeYear,
  pageOf,
} from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import { ExamDetailPage } from '../admin/ExamDetailPage';
import { ExamsPage } from '../admin/ExamsPage';
import { AdminResultsPage } from '../admin/AdminResultsPage';
import { GradeRulesPage } from '../admin/GradeRulesPage';
import { StudentReportCardPage } from '../student/StudentReportCardPage';
import { StudentResultsPage } from '../student/StudentResultsPage';
import { TeacherMarksEntryPage } from './TeacherMarksEntryPage';
import { TeacherMarksPage } from './TeacherMarksPage';

const api = vi.hoisted(() => ({
  listGradeRules: vi.fn(),
  saveGradeRule: vi.fn(),
  deleteGradeRule: vi.fn(),
  listExams: vi.fn(),
  getExam: vi.fn(),
  saveExam: vi.fn(),
  deleteExam: vi.fn(),
  transitionExam: vi.fn(),
  getProgress: vi.fn(),
  getResults: vi.fn(),
  getStudentReportCard: vi.fn(),
  listTeacherExams: vi.fn(),
  getMarksSheet: vi.fn(),
  saveMarks: vi.fn(),
  getCommentSheet: vi.fn(),
  saveComment: vi.fn(),
  listMyResults: vi.fn(),
  getMyReportCard: vi.fn(),
}));
const academics = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  listGrades: vi.fn(),
  listSubjects: vi.fn(),
  listSections: vi.fn(),
}));
vi.mock('../../features/exams/api', () => api);
vi.mock('../../features/academics/api', () => academics);

const asAdmin = (ui: React.ReactElement, route = '/admin/exams') =>
  renderWithProviders(ui, { route, auth: { user: makeUser({ roles: ['ROLE_ADMIN'] }) } });
const asTeacher = (ui: React.ReactElement, route = '/teacher/marks') =>
  renderWithProviders(ui, { route, auth: { user: makeUser({ roles: ['ROLE_TEACHER'] }) } });
const asStudent = (ui: React.ReactElement, route = '/student/results') =>
  renderWithProviders(ui, { route, auth: { user: makeUser() } });
const at = (path: string, element: React.ReactElement) => (
  <Routes>
    <Route path={path} element={element} />
  </Routes>
);

beforeEach(() => {
  setViewportWidth(1440);
  [...Object.values(api), ...Object.values(academics)].forEach((mock) => mock.mockReset());
  academics.listAcademicYears.mockResolvedValue(pageOf([makeYear({ status: 'ACTIVE' })]));
  academics.listGrades.mockResolvedValue(pageOf([makeGrade()]));
  academics.listSubjects.mockResolvedValue(
    pageOf([makeSubject(), makeSubject({ id: 'subject-2', code: 'ENG', name: 'English' })]),
  );
  academics.listSections.mockResolvedValue(pageOf([makeSection()]));
});

describe('GradeRulesPage', () => {
  it('shows the scale and whether it covers 0 to 100', async () => {
    api.listGradeRules.mockResolvedValue(
      pageOf([
        makeGradeRule({ id: 'a', grade: 'A', minPercent: 50, maxPercent: 100 }),
        makeGradeRule({ id: 'f', grade: 'F', minPercent: 0, maxPercent: 49.99 }),
      ]),
    );
    asAdmin(<GradeRulesPage />, '/admin/grade-rules');
    expect(await screen.findByText(/covers every percentage from 0% to 100%/)).toBeInTheDocument();

    api.listGradeRules.mockResolvedValue(pageOf([makeGradeRule()]));
    asAdmin(<GradeRulesPage />, '/admin/grade-rules');
    expect(await screen.findByText('The scale does not cover 0% to 100%')).toBeInTheDocument();
  });

  it('adds a band with numeric percentages and refuses a range that runs backwards', async () => {
    api.listGradeRules.mockResolvedValue(pageOf([]));
    api.saveGradeRule.mockResolvedValue(makeGradeRule());
    asAdmin(<GradeRulesPage />, '/admin/grade-rules');
    await userEvent.click(await screen.findByRole('button', { name: 'New grade band' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^grade/i), 'A');
    await userEvent.type(within(dialog).getByLabelText(/from/i), '90');
    await userEvent.type(within(dialog).getByLabelText(/to \(/i), '80');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(api.saveGradeRule).not.toHaveBeenCalled();
    expect(
      await within(dialog).findByText(/not be (less|below)|after|more than/i),
    ).toBeInTheDocument();

    await userEvent.clear(within(dialog).getByLabelText(/to \(/i));
    await userEvent.type(within(dialog).getByLabelText(/to \(/i), '100');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() =>
      expect(api.saveGradeRule).toHaveBeenCalledWith(
        undefined,
        expect.objectContaining({ grade: 'A', minPercent: 90, maxPercent: 100 }),
      ),
    );
  });

  it('shows an overlap reported by the server inside the dialog', async () => {
    api.listGradeRules.mockResolvedValue(pageOf([]));
    api.saveGradeRule.mockRejectedValue(
      new ApiError('These percentages overlap another grade.', { status: 409 }),
    );
    asAdmin(<GradeRulesPage />, '/admin/grade-rules');
    await userEvent.click(await screen.findByRole('button', { name: 'New grade band' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^grade/i), 'B');
    await userEvent.type(within(dialog).getByLabelText(/from/i), '60');
    await userEvent.type(within(dialog).getByLabelText(/to \(/i), '80');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('overlap');
  });
});

describe('ExamsPage', () => {
  it('creates an exam with subjects, defaulting pass marks to 35% of the maximum', async () => {
    api.listExams.mockResolvedValue(pageOf([]));
    api.saveExam.mockResolvedValue(makeExam());
    asAdmin(<ExamsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /new exam/i }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Term 1');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /academic year/i }));
    await userEvent.click(await screen.findByRole('option', { name: '2026-2027' }));
    await userEvent.click(within(dialog).getByRole('combobox', { name: /^grade/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8' }));
    const first = within(within(dialog).getByRole('group', { name: 'Subject 1' }));
    await userEvent.click(first.getByRole('combobox', { name: /subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Mathematics (MATH)' }));
    expect(first.getByLabelText(/pass marks/i)).toHaveValue('35');
    await userEvent.clear(first.getByLabelText(/maximum marks/i));
    await userEvent.type(first.getByLabelText(/maximum marks/i), '50');
    expect(first.getByLabelText(/pass marks/i)).toHaveValue('17.5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create exam' }));

    await waitFor(() => expect(api.saveExam).toHaveBeenCalled());
    expect(api.saveExam.mock.calls[0]).toEqual([
      undefined,
      {
        academicYearId: 'year-1',
        gradeLevelId: 'grade-1',
        name: 'Term 1',
        startDate: null,
        endDate: null,
        subjects: [{ subjectId: 'subject-1', maxMarks: 50, passMarks: 17.5, examDate: null }],
        version: undefined,
      },
    ]);
  });

  it('will not submit pass marks above the maximum', async () => {
    api.listExams.mockResolvedValue(pageOf([]));
    asAdmin(<ExamsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /new exam/i }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Term 1');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /academic year/i }));
    await userEvent.click(await screen.findByRole('option', { name: '2026-2027' }));
    await userEvent.click(within(dialog).getByRole('combobox', { name: /^grade/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8' }));
    const first = within(within(dialog).getByRole('group', { name: 'Subject 1' }));
    await userEvent.click(first.getByRole('combobox', { name: /subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Mathematics (MATH)' }));
    await userEvent.clear(first.getByLabelText(/pass marks/i));
    await userEvent.type(first.getByLabelText(/pass marks/i), '120');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create exam' }));
    expect(api.saveExam).not.toHaveBeenCalled();
    expect(
      await within(dialog).findByText(/cannot be more than|exceed|at most/i),
    ).toBeInTheDocument();
  });
});

describe('ExamDetailPage', () => {
  const render = () => asAdmin(at('/admin/exams/:id', <ExamDetailPage />), '/admin/exams/exam-1');

  it('asks for confirmation before opening a draft exam for marks', async () => {
    api.getExam.mockResolvedValue(makeExam());
    api.getProgress.mockResolvedValue({
      examId: 'exam-1',
      status: 'DRAFT',
      complete: true,
      missing: 0,
      rows: [],
    });
    api.transitionExam.mockResolvedValue(makeExam({ status: 'OPEN_FOR_MARKS' }));
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Open for marks' }));
    const dialog = await screen.findByRole('dialog');
    expect(api.transitionExam).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: /open/i }));
    await waitFor(() => expect(api.transitionExam).toHaveBeenCalledWith('exam-1', 'open'));
  });

  it('shows what is still missing and that locking will be refused', async () => {
    api.getExam.mockResolvedValue(makeExam({ status: 'OPEN_FOR_MARKS' }));
    api.getProgress.mockResolvedValue({
      examId: 'exam-1',
      status: 'OPEN_FOR_MARKS',
      complete: false,
      missing: 2,
      rows: [
        {
          examSubjectId: 'es-1',
          subjectName: 'Mathematics',
          sectionId: 'section-1',
          sectionName: 'Grade 8 - A',
          teacherName: 'Milo Math',
          expected: 3,
          entered: 1,
        },
      ],
    });
    api.transitionExam.mockRejectedValue(
      new ApiError('Marks are missing for 2 student entries.', { status: 422 }),
    );
    render();
    expect(await screen.findByText('2 missing')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Lock marks' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('2 entries are still missing');
    await userEvent.click(within(dialog).getByRole('button', { name: /lock/i }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Marks are missing');
  });

  it('warns that publishing is final', async () => {
    api.getExam.mockResolvedValue(makeExam({ status: 'LOCKED' }));
    api.getProgress.mockResolvedValue({
      examId: 'exam-1',
      status: 'LOCKED',
      complete: true,
      missing: 0,
      rows: [],
    });
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Publish results' }));
    expect(await screen.findByRole('dialog')).toHaveTextContent(
      /cannot be undone|final|permanent/i,
    );
    expect(screen.getByText('Preview results').closest('a')).toHaveAttribute(
      'href',
      '/admin/results?examId=exam-1',
    );
  });
});

describe('AdminResultsPage', () => {
  it('labels results of a locked exam as a preview', async () => {
    api.listExams.mockResolvedValue(pageOf([makeExam({ status: 'LOCKED' })]));
    api.getResults.mockResolvedValue({
      examId: 'exam-1',
      examName: 'Term 1',
      status: 'LOCKED',
      preview: true,
      sectionId: 'section-1',
      results: [makeStudentResult()],
    });
    asAdmin(<AdminResultsPage />, '/admin/results?examId=exam-1&sectionId=section-1');
    expect(await screen.findByText(/Preview - not published/)).toBeInTheDocument();
    expect(await screen.findByText('Ann Pupil')).toBeInTheDocument();
    expect(screen.getByText('66.67%')).toBeInTheDocument();
  });
});

describe('TeacherMarksPage', () => {
  it('lists the subjects and classes to mark with their progress', async () => {
    api.listTeacherExams.mockResolvedValue(
      pageOf([
        {
          id: 'exam-1',
          name: 'Term 1',
          status: 'OPEN_FOR_MARKS',
          academicYearName: '2026-2027',
          gradeLevelName: 'Grade 8',
          startDate: null,
          endDate: null,
          tasks: [
            {
              examSubjectId: 'es-1',
              subjectName: 'Mathematics',
              sectionId: 'section-1',
              sectionName: 'Grade 8 - A',
              maxMarks: 100,
              passMarks: 35,
              expected: 2,
              entered: 1,
            },
          ],
          commentSections: [{ id: 'section-1', name: 'Grade 8 - A' }],
        },
      ]),
    );
    asTeacher(<TeacherMarksPage />);
    expect(await screen.findByText('1 of 2 entered')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Enter marks: Mathematics, Grade 8 - A' }),
    ).toHaveAttribute('href', '/teacher/marks/exam-1/es-1/section-1');
    expect(screen.getByRole('link', { name: 'Comments for Grade 8 - A' })).toBeInTheDocument();
  });
});

describe('TeacherMarksEntryPage', () => {
  const render = () =>
    asTeacher(
      at('/teacher/marks/:examId/:examSubjectId/:sectionId', <TeacherMarksEntryPage />),
      '/teacher/marks/exam-1/es-1/section-1',
    );

  it('sends only the rows that changed, with numbers, and applies what the server returns', async () => {
    api.getMarksSheet.mockResolvedValue(makeMarksSheet());
    api.saveMarks.mockResolvedValue(
      makeMarksSheet({
        rows: [
          { ...makeMarksSheet().rows[0]!, marks: 87.5, entered: true },
          makeMarksSheet().rows[1]!,
        ],
      }),
    );
    render();
    await userEvent.type(await screen.findByLabelText('Marks for Ann Pupil'), '87.5');
    expect(screen.getByRole('status')).toHaveTextContent('Unsaved changes for 1 student');
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));

    await waitFor(() =>
      expect(api.saveMarks).toHaveBeenCalledWith('exam-1', 'es-1', 'section-1', {
        entries: [{ studentId: 'st-1', marks: 87.5, absent: false, remarks: null }],
      }),
    );
    expect(await screen.findByText('All changes saved · 1 of 2 entered')).toBeInTheDocument();
  });

  it('refuses marks above the maximum before calling the server', async () => {
    api.getMarksSheet.mockResolvedValue(makeMarksSheet());
    render();
    await userEvent.type(await screen.findByLabelText('Marks for Ann Pupil'), '101');
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));
    expect(await screen.findByText(/Fix the marks marked in red/)).toBeInTheDocument();
    expect(screen.getAllByText(/more than 100|at most 100/i).length).toBeGreaterThan(0);
    expect(api.saveMarks).not.toHaveBeenCalled();
  });

  it('marks a student absent, which clears and disables the marks and sends no marks', async () => {
    api.getMarksSheet.mockResolvedValue(makeMarksSheet());
    api.saveMarks.mockResolvedValue(makeMarksSheet());
    render();
    const input = await screen.findByLabelText('Marks for Ann Pupil');
    await userEvent.type(input, '50');
    await userEvent.click(screen.getByLabelText('Absent: Ann Pupil'));
    expect(input).toBeDisabled();
    expect(input).toHaveValue('');
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));
    await waitFor(() =>
      expect(api.saveMarks).toHaveBeenCalledWith('exam-1', 'es-1', 'section-1', {
        entries: [{ studentId: 'st-1', marks: null, absent: true, remarks: null }],
      }),
    );
  });

  it('is read-only once the exam is locked', async () => {
    api.getMarksSheet.mockResolvedValue(
      makeMarksSheet({
        status: 'LOCKED',
        editable: false,
        rows: [{ ...makeMarksSheet().rows[0]!, marks: 70, entered: true }],
      }),
    );
    render();
    expect(await screen.findByText(/read-only because this exam is locked/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save marks' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Marks for Ann Pupil')).not.toBeInTheDocument();
    expect(screen.getByText('70')).toBeInTheDocument();
  });

  it('shows the server field errors against the right student', async () => {
    api.getMarksSheet.mockResolvedValue(makeMarksSheet());
    api.saveMarks.mockRejectedValue(
      new ApiError('One or more request fields are invalid.', {
        status: 400,
        fieldErrors: { 'entries[0].marks': 'Marks cannot be more than 100' },
      }),
    );
    render();
    await userEvent.type(await screen.findByLabelText('Marks for Ann Pupil'), '60');
    await userEvent.click(screen.getByRole('button', { name: 'Save marks' }));
    expect(await screen.findByText('Marks cannot be more than 100')).toBeInTheDocument();
  });

  it('stacks students as cards on a phone', async () => {
    setViewportWidth(375);
    api.getMarksSheet.mockResolvedValue(makeMarksSheet());
    render();
    const list = await screen.findByRole('list', { name: 'Marks' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.queryByRole('table', { name: 'Marks' })).not.toBeInTheDocument();
  });
});

describe('Student results', () => {
  it('lists published exams and opens a report card', async () => {
    api.listMyResults.mockResolvedValue(
      pageOf([
        {
          examId: 'exam-1',
          examName: 'Term 1',
          academicYearName: '2026-2027',
          gradeLevelName: 'Grade 8',
          publishedAt: '2026-11-18T10:00:00Z',
          percentage: 86.67,
          grade: 'A',
          result: 'PASS',
        },
      ]),
    );
    asStudent(<StudentResultsPage />);
    expect(await screen.findByText('Term 1')).toBeInTheDocument();
    expect(screen.getByText('86.67%')).toBeInTheDocument();
    expect(screen.getByText(/^pass$/i)).toBeInTheDocument();
  });

  it('has a friendly empty state before anything is published', async () => {
    api.listMyResults.mockResolvedValue(pageOf([]));
    asStudent(<StudentResultsPage />);
    expect(await screen.findByText('No results yet')).toBeInTheDocument();
  });

  it('renders the report card with every subject, the totals and the comment, and prints it', async () => {
    api.getMyReportCard.mockResolvedValue(makeReportCard());
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    asStudent(
      at('/student/report-cards/:examId', <StudentReportCardPage />),
      '/student/report-cards/exam-1',
    );

    const card = await screen.findByLabelText('Report card of Ann Pupil');
    expect(within(card).getByText('Demo School')).toBeInTheDocument();
    const table = within(card).getByRole('table', { name: 'Subject results' });
    expect(within(table).getByText('Mathematics')).toBeInTheDocument();
    expect(within(table).getByText('English')).toBeInTheDocument();
    expect(within(card).getByText('66.67%')).toBeInTheDocument();
    expect(within(card).getByText('Work on English')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Print' }));
    expect(print).toHaveBeenCalledTimes(1);
    print.mockRestore();
  });

  it('shows an absent subject as absent, not as zero', async () => {
    api.getMyReportCard.mockResolvedValue(
      makeReportCard({
        result: makeStudentResult({
          subjects: [
            {
              subjectId: 'subject-1',
              subjectCode: 'MATH',
              subjectName: 'Mathematics',
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
        }),
      }),
    );
    asStudent(
      at('/student/report-cards/:examId', <StudentReportCardPage />),
      '/student/report-cards/exam-1',
    );
    const table = within(await screen.findByRole('table', { name: 'Subject results' }));
    expect(table.getAllByText(/^absent$/i).length).toBeGreaterThan(0);
  });

  it('explains when a report card is not available', async () => {
    api.getMyReportCard.mockRejectedValue(new ApiError('Result was not found.', { status: 404 }));
    asStudent(
      at('/student/report-cards/:examId', <StudentReportCardPage />),
      '/student/report-cards/exam-9',
    );
    expect(await screen.findByText('Unable to load the report card')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('link', { name: 'Report cards' }));
  });
});
