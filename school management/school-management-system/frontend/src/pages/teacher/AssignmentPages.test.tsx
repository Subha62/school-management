import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { pageOf } from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import type {
  Assignment,
  StudentAssignment,
  Submission,
  SubmissionListItem,
} from '../../types/assignments';
import { StudentAssignmentDetailPage } from '../student/StudentAssignmentDetailPage';
import { StudentAssignmentsPage } from '../student/StudentAssignmentsPage';
import { StudentSubmissionsPage } from '../student/StudentSubmissionsPage';
import { TeacherAssignmentDetailPage } from './TeacherAssignmentDetailPage';
import { TeacherAssignmentListPage } from './TeacherAssignmentsPage';
import { TeacherSubmissionsPage } from './TeacherSubmissionsPage';

const api = vi.hoisted(() => ({
  listAssignments: vi.fn(),
  getAssignment: vi.fn(),
  saveAssignment: vi.fn(),
  deleteAssignment: vi.fn(),
  transitionAssignment: vi.fn(),
  addAssignmentFiles: vi.fn(),
  removeAssignmentFile: vi.fn(),
  listRoster: vi.fn(),
  getSubmission: vi.fn(),
  reviewSubmission: vi.fn(),
  listReviewQueue: vi.fn(),
  listStudentAssignments: vi.fn(),
  getStudentAssignment: vi.fn(),
  submitWork: vi.fn(),
  removeSubmissionFile: vi.fn(),
  listMySubmissions: vi.fn(),
  downloadFile: vi.fn(),
  teacherAssignmentFilePath: (id: string, file: string) => `/t/${id}/${file}`,
  teacherSubmissionFilePath: (id: string, sub: string, file: string) => `/t/${id}/${sub}/${file}`,
  studentAssignmentFilePath: (id: string, file: string) => `/s/${id}/${file}`,
  studentOwnFilePath: (id: string, file: string) => `/s/${id}/own/${file}`,
}));
const operations = vi.hoisted(() => ({ listMyClasses: vi.fn() }));
vi.mock('../../features/assignments/api', () => api);
vi.mock('../../features/operations/api', async (original) => ({
  ...(await original<object>()),
  ...operations,
}));

const FUTURE = new Date(Date.now() + 7 * 86_400_000).toISOString();
const PAST = new Date(Date.now() - 86_400_000).toISOString();

const attachment = {
  id: 'file-1',
  name: 'sheet.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedAt: PAST,
};

const assignment = (overrides: Partial<Assignment> = {}): Assignment => ({
  id: 'as-1',
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  academicYearName: '2026-2027',
  subjectId: 'subject-1',
  subjectName: 'Mathematics',
  teacherId: 'teacher-1',
  teacherName: 'Tara Teacher',
  title: 'Worksheet 1',
  description: 'Do all of it',
  dueAt: FUTURE,
  maxMarks: 10,
  allowLate: false,
  status: 'PUBLISHED',
  publishedAt: PAST,
  closedAt: null,
  attachments: [],
  counts: { enrolled: 2, submitted: 1, awaitingReview: 1, reviewed: 0 },
  version: 0,
  ...overrides,
});

const submission = (overrides: Partial<Submission> = {}): Submission => ({
  id: 'sub-1',
  assignmentId: 'as-1',
  studentId: 'st-1',
  admissionNumber: 'S-0001',
  studentName: 'Sam Student',
  status: 'SUBMITTED',
  textAnswer: 'My answer',
  submittedAt: PAST,
  marks: null,
  feedback: null,
  reviewedAt: null,
  reviewedBy: null,
  attachments: [],
  version: 3,
  ...overrides,
});

const queueItem = (overrides: Partial<SubmissionListItem> = {}): SubmissionListItem => ({
  id: 'sub-1',
  assignmentId: 'as-1',
  assignmentTitle: 'Worksheet 1',
  subjectName: 'Mathematics',
  sectionName: 'Grade 8 - A',
  studentId: 'st-1',
  admissionNumber: 'S-0001',
  studentName: 'Sam Student',
  status: 'SUBMITTED',
  submittedAt: PAST,
  dueAt: FUTURE,
  marks: null,
  maxMarks: 10,
  ...overrides,
});

const forStudent = (overrides: Partial<StudentAssignment> = {}): StudentAssignment => ({
  id: 'as-1',
  subjectName: 'Mathematics',
  sectionName: 'Grade 8 - A',
  teacherName: 'Tara Teacher',
  title: 'Worksheet 1',
  description: 'Do all of it',
  dueAt: FUTURE,
  maxMarks: 10,
  allowLate: false,
  status: 'PUBLISHED',
  attachments: [],
  submission: null,
  canSubmit: true,
  ...overrides,
});

const asTeacher = (ui: React.ReactElement, route = '/teacher/assignments') =>
  renderWithProviders(ui, {
    route,
    auth: { user: makeUser({ roles: ['ROLE_TEACHER'] }) },
  });
const asStudent = (ui: React.ReactElement, route = '/student/assignments') =>
  renderWithProviders(ui, { route, auth: { user: makeUser() } });
const at = (path: string, element: React.ReactElement) => (
  <Routes>
    <Route path={path} element={element} />
  </Routes>
);

beforeEach(() => {
  setViewportWidth(1440);
  [...Object.values(api), ...Object.values(operations)].forEach((mock) => {
    if (typeof mock === 'function' && 'mockReset' in mock) mock.mockReset();
  });
  operations.listMyClasses.mockResolvedValue([
    {
      sectionId: 'section-1',
      sectionName: 'Grade 8 - A',
      academicYear: '2026-2027',
      daily: true,
      subjects: [{ id: 'subject-1', name: 'Mathematics' }],
    },
  ]);
});

describe('TeacherAssignmentListPage', () => {
  beforeEach(() => {
    api.listAssignments.mockResolvedValue(pageOf([assignment()]));
  });

  it('lists the assignments with how much work is waiting', async () => {
    asTeacher(<TeacherAssignmentListPage />);
    expect(await screen.findByRole('link', { name: 'Worksheet 1' })).toBeInTheDocument();
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    expect(screen.getByText('1 to review')).toBeInTheDocument();
  });

  it('creates a draft for a class and subject the teacher teaches', async () => {
    api.saveAssignment.mockResolvedValue(assignment({ status: 'DRAFT' }));
    asTeacher(<TeacherAssignmentListPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New assignment' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('combobox', { name: /class and subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8 - A · Mathematics' }));
    await userEvent.type(within(dialog).getByLabelText(/^title/i), 'Fractions');
    fireEvent.change(within(dialog).getByLabelText(/due date/i), {
      target: { value: '2099-01-02T09:30' },
    });
    await userEvent.type(within(dialog).getByLabelText(/maximum marks/i), '25');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create draft' }));

    await waitFor(() => expect(api.saveAssignment).toHaveBeenCalled());
    const [id, body] = api.saveAssignment.mock.calls[0]!;
    expect(id).toBeUndefined();
    expect(body).toMatchObject({
      sectionId: 'section-1',
      subjectId: 'subject-1',
      title: 'Fractions',
      description: null,
      maxMarks: 25,
      allowLate: false,
    });
    expect(new Date(body.dueAt as string).getFullYear()).toBe(2099);
  });

  it('refuses a due date in the past before calling the server', async () => {
    asTeacher(<TeacherAssignmentListPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New assignment' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /class and subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8 - A · Mathematics' }));
    await userEvent.type(within(dialog).getByLabelText(/^title/i), 'Old');
    fireEvent.change(within(dialog).getByLabelText(/due date/i), {
      target: { value: '2001-01-02T09:30' },
    });
    await userEvent.type(within(dialog).getByLabelText(/maximum marks/i), '10');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create draft' }));

    expect(
      await within(dialog).findByText('The due date must be in the future'),
    ).toBeInTheDocument();
    expect(api.saveAssignment).not.toHaveBeenCalled();
  });

  it('says so when the teacher has no subject to set work for', async () => {
    operations.listMyClasses.mockResolvedValue([]);
    asTeacher(<TeacherAssignmentListPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New assignment' }));
    await userEvent.click(
      await within(await screen.findByRole('dialog')).findByRole('combobox', {
        name: /class and subject/i,
      }),
    );
    expect(
      await screen.findByText('You have no subject assigned in the active academic year.'),
    ).toBeInTheDocument();
  });
});

describe('TeacherAssignmentDetailPage', () => {
  const render = () =>
    asTeacher(
      at('/teacher/assignments/:id', <TeacherAssignmentDetailPage />),
      '/teacher/assignments/as-1',
    );

  beforeEach(() => {
    api.getAssignment.mockResolvedValue(assignment({ attachments: [attachment] }));
    api.listRoster.mockResolvedValue([
      {
        studentId: 'st-1',
        admissionNumber: 'S-0001',
        studentName: 'Sam Student',
        rollNumber: 1,
        submissionId: 'sub-1',
        status: 'SUBMITTED',
        submittedAt: PAST,
        marks: null,
      },
      {
        studentId: 'st-2',
        admissionNumber: 'S-0002',
        studentName: 'Ann Pupil',
        rollNumber: 2,
        submissionId: null,
        status: null,
        submittedAt: null,
        marks: null,
      },
    ]);
  });

  it('shows the roster, including students who have not handed in', async () => {
    render();
    expect(await screen.findByText('Sam Student (S-0001)')).toBeInTheDocument();
    expect(screen.getByText('Ann Pupil (S-0002)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Review Sam Student' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Review Ann Pupil' })).not.toBeInTheDocument();
  });

  it('downloads an attachment through the API', async () => {
    api.downloadFile.mockResolvedValue(undefined);
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Download sheet.pdf' }));
    expect(api.downloadFile).toHaveBeenCalledWith('/t/as-1/file-1', 'sheet.pdf');
  });

  it('reports a failed download instead of throwing', async () => {
    api.downloadFile.mockRejectedValue(
      new ApiError('That file is no longer available.', { status: 404 }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Download sheet.pdf' }));
    expect(await screen.findByText('That file is no longer available.')).toBeInTheDocument();
  });

  it('confirms before closing the assignment', async () => {
    api.transitionAssignment.mockResolvedValue(assignment({ status: 'CLOSED' }));
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Close' }));
    const dialog = await screen.findByRole('dialog');
    expect(api.transitionAssignment).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(api.transitionAssignment).toHaveBeenCalledWith('as-1', 'close'));
  });

  it('refuses a file of a disallowed type before uploading it', async () => {
    render();
    await screen.findByText('Sam Student (S-0001)');
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    await userEvent.upload(
      input,
      new File(['x'], 'virus.exe', { type: 'application/octet-stream' }),
      { applyAccept: false },
    );
    expect(await screen.findByText(/"virus.exe" is not an allowed type/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Upload/ })).not.toBeInTheDocument();
    expect(api.addAssignmentFiles).not.toHaveBeenCalled();
  });

  it('uploads the chosen files', async () => {
    api.addAssignmentFiles.mockResolvedValue(assignment());
    render();
    await screen.findByText('Sam Student (S-0001)');
    const input = document.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['%PDF-1.4'], 'extra.pdf', { type: 'application/pdf' });
    await userEvent.upload(input, file);
    await userEvent.click(await screen.findByRole('button', { name: 'Upload 1 file' }));
    await waitFor(() => expect(api.addAssignmentFiles).toHaveBeenCalledWith('as-1', [file]));
  });

  it('does not offer file changes on a closed assignment', async () => {
    api.getAssignment.mockResolvedValue(
      assignment({ status: 'CLOSED', attachments: [attachment] }),
    );
    render();
    expect(await screen.findByRole('button', { name: 'Reopen' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remove sheet.pdf' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Choose files' })).not.toBeInTheDocument();
  });

  it('reviews a submission with marks and feedback', async () => {
    api.getSubmission.mockResolvedValue(submission());
    api.reviewSubmission.mockResolvedValue(submission({ status: 'REVIEWED', marks: 8.5 }));
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Review Sam Student' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('My answer')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/^marks/i), '8.5');
    await userEvent.type(within(dialog).getByLabelText(/feedback/i), 'Well done');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save review' }));

    await waitFor(() =>
      expect(api.reviewSubmission).toHaveBeenCalledWith('as-1', 'sub-1', {
        outcome: 'REVIEWED',
        marks: 8.5,
        feedback: 'Well done',
        version: 3,
      }),
    );
  });

  it('does not accept marks above the maximum', async () => {
    api.getSubmission.mockResolvedValue(submission());
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Review Sam Student' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(await within(dialog).findByLabelText(/^marks/i), '11');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save review' }));
    expect(await within(dialog).findByText('Marks cannot be more than 10')).toBeInTheDocument();
    expect(api.reviewSubmission).not.toHaveBeenCalled();
  });

  it('asks for feedback when requesting a resubmission, and sends no marks', async () => {
    api.getSubmission.mockResolvedValue(submission());
    api.reviewSubmission.mockResolvedValue(submission({ status: 'RESUBMISSION_REQUESTED' }));
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Review Sam Student' }));
    const dialog = await screen.findByRole('dialog');
    await screen.findByText('My answer');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Request resubmission' }));
    expect(
      await within(dialog).findByText('Say what the student should improve'),
    ).toBeInTheDocument();
    expect(api.reviewSubmission).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/feedback/i), 'Show your working');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Request resubmission' }));
    await waitFor(() =>
      expect(api.reviewSubmission).toHaveBeenCalledWith('as-1', 'sub-1', {
        outcome: 'RESUBMISSION_REQUESTED',
        marks: null,
        feedback: 'Show your working',
        version: 3,
      }),
    );
  });

  it('shows a stale-version conflict inside the review dialog', async () => {
    api.getSubmission.mockResolvedValue(submission());
    api.reviewSubmission.mockRejectedValue(
      new ApiError('The submission changed. Reload and try again.', { status: 409 }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Review Sam Student' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(await within(dialog).findByLabelText(/^marks/i), '5');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save review' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('The submission changed');
  });
});

describe('TeacherSubmissionsPage', () => {
  it('shows work waiting for review by default and filters by status', async () => {
    api.listReviewQueue.mockResolvedValue(pageOf([queueItem()]));
    asTeacher(<TeacherSubmissionsPage />, '/teacher/submissions');
    expect(await screen.findByText('Sam Student (S-0001)')).toBeInTheDocument();
    expect(api.listReviewQueue).toHaveBeenLastCalledWith(
      expect.objectContaining({ pending: true, status: undefined }),
    );

    await userEvent.click(screen.getByRole('combobox', { name: 'Show' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Late' }));
    await waitFor(() =>
      expect(api.listReviewQueue).toHaveBeenLastCalledWith(
        expect.objectContaining({ pending: false, status: 'LATE' }),
      ),
    );
  });

  it('has a friendly empty state', async () => {
    api.listReviewQueue.mockResolvedValue(pageOf([]));
    asTeacher(<TeacherSubmissionsPage />, '/teacher/submissions');
    expect(await screen.findByText('Nothing is waiting for review')).toBeInTheDocument();
  });
});

describe('StudentAssignmentsPage', () => {
  it('lists assignments with the student’s own status and marks', async () => {
    api.listStudentAssignments.mockResolvedValue(
      pageOf([
        forStudent(),
        forStudent({
          id: 'as-2',
          title: 'Reading log',
          status: 'CLOSED',
          canSubmit: false,
          submission: submission({ status: 'REVIEWED', marks: 8.5 }),
        }),
      ]),
    );
    asStudent(<StudentAssignmentsPage />);
    expect(await screen.findByRole('link', { name: 'Worksheet 1' })).toBeInTheDocument();
    expect(screen.getByText('8.5 / 10')).toBeInTheDocument();
    expect(screen.getByText('Closed')).toBeInTheDocument();
  });

  it('flags work that is overdue and not handed in', async () => {
    api.listStudentAssignments.mockResolvedValue(pageOf([forStudent({ dueAt: PAST })]));
    asStudent(<StudentAssignmentsPage />);
    expect(await screen.findByText('Overdue')).toBeInTheDocument();
  });
});

describe('StudentAssignmentDetailPage', () => {
  const render = () =>
    asStudent(
      at('/student/assignments/:id', <StudentAssignmentDetailPage />),
      '/student/assignments/as-1',
    );

  it('submits a written answer with a file as multipart data', async () => {
    api.getStudentAssignment.mockResolvedValue(forStudent());
    api.submitWork.mockResolvedValue(forStudent({ submission: submission() }));
    render();
    await userEvent.type(
      await screen.findByLabelText('Your answer', { selector: 'textarea' }),
      'Done',
    );
    const file = new File(['%PDF-1.4'], 'work.pdf', { type: 'application/pdf' });
    await userEvent.upload(document.querySelector<HTMLInputElement>('input[type="file"]')!, file);
    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));
    await waitFor(() => expect(api.submitWork).toHaveBeenCalledWith('as-1', 'Done', [file]));
  });

  it('refuses an empty hand-in message from the server and keeps the form', async () => {
    api.getStudentAssignment.mockResolvedValue(forStudent());
    api.submitWork.mockRejectedValue(
      new ApiError('Write an answer or attach at least one file.', { status: 400 }),
    );
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Write an answer or attach');
    expect(screen.getByRole('button', { name: 'Submit' })).toBeInTheDocument();
  });

  it('warns that late work will be marked late', async () => {
    api.getStudentAssignment.mockResolvedValue(forStudent({ dueAt: PAST, allowLate: true }));
    render();
    expect(await screen.findByText(/will be marked as late/)).toBeInTheDocument();
  });

  it('explains why a closed assignment cannot be submitted', async () => {
    api.getStudentAssignment.mockResolvedValue(forStudent({ status: 'CLOSED', canSubmit: false }));
    render();
    expect(await screen.findByText(/no longer takes submissions/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
  });

  it('shows marks and feedback once reviewed and offers no way to change the work', async () => {
    api.getStudentAssignment.mockResolvedValue(
      forStudent({
        canSubmit: false,
        submission: submission({
          status: 'REVIEWED',
          marks: 8.5,
          feedback: 'Great',
          attachments: [attachment],
        }),
      }),
    );
    render();
    expect(await screen.findByText(/Marks: 8.5 \/ 10/)).toHaveTextContent('Great');
    expect(screen.queryByRole('button', { name: /^Remove/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Update submission' })).not.toBeInTheDocument();
  });

  it('lets the student try again after a resubmission request', async () => {
    api.getStudentAssignment.mockResolvedValue(
      forStudent({
        dueAt: PAST,
        submission: submission({ status: 'RESUBMISSION_REQUESTED', feedback: 'Show working' }),
      }),
    );
    render();
    expect(await screen.findByText(/asked you to try again: Show working/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Update submission' })).toBeInTheDocument();
    expect(screen.queryByText(/will be marked as late/)).not.toBeInTheDocument();
  });

  it('removes one of the student’s own files', async () => {
    api.getStudentAssignment.mockResolvedValue(
      forStudent({ submission: submission({ attachments: [attachment] }) }),
    );
    api.removeSubmissionFile.mockResolvedValue(forStudent());
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'Remove sheet.pdf' }));
    await waitFor(() => expect(api.removeSubmissionFile).toHaveBeenCalledWith('as-1', 'file-1'));
  });
});

describe('StudentSubmissionsPage', () => {
  it('shows marks only for reviewed work', async () => {
    api.listMySubmissions.mockResolvedValue(
      pageOf([
        queueItem({ status: 'REVIEWED', marks: 8.5 }),
        queueItem({ id: 'sub-2', assignmentId: 'as-2', assignmentTitle: 'Reading log' }),
      ]),
    );
    asStudent(<StudentSubmissionsPage />, '/student/submissions');
    expect(await screen.findByText('8.5 / 10')).toBeInTheDocument();
    expect(screen.getByText('Not marked yet')).toBeInTheDocument();
  });
});
