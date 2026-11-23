import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../../api/errors';
import {
  makeAdminUser,
  makeEnrollment,
  makeGrade,
  makeGuardian,
  makeGuardianLink,
  makeSection,
  makeStudent,
  makeYear,
  pageOf,
} from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import { EnrollmentsPage } from './EnrollmentsPage';
import { GuardiansPage } from './GuardiansPage';
import { PromotionPage } from './PromotionPage';
import { StudentDetailPage } from './StudentDetailPage';
import { StudentsPage } from './StudentsPage';
import { TeachersPage } from './TeachersPage';
import { UsersPage } from './UsersPage';
import { Route, Routes } from 'react-router-dom';

const people = vi.hoisted(() => ({
  listUsers: vi.fn(),
  createAdmin: vi.fn(),
  changeUserStatus: vi.fn(),
  unlockUser: vi.fn(),
  listStudents: vi.fn(),
  getStudent: vi.fn(),
  saveStudent: vi.fn(),
  listStudentEnrollments: vi.fn(),
  listGuardians: vi.fn(),
  saveGuardian: vi.fn(),
  deleteGuardian: vi.fn(),
  listStudentGuardians: vi.fn(),
  saveGuardianLink: vi.fn(),
  unlinkGuardian: vi.fn(),
  listTeachers: vi.fn(),
  saveTeacher: vi.fn(),
  listEnrollments: vi.fn(),
  enrollStudent: vi.fn(),
  transferEnrollment: vi.fn(),
  withdrawEnrollment: vi.fn(),
  completeEnrollment: vi.fn(),
  promoteStudents: vi.fn(),
  listPromotions: vi.fn(),
}));
const academics = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  listGrades: vi.fn(),
  listSections: vi.fn(),
  listTeacherOptions: vi.fn(),
}));
vi.mock('../../features/people/api', () => people);
vi.mock('../../features/academics/api', () => academics);

const year2026 = makeYear({
  id: 'year-1',
  name: '2026-2027',
  startDate: '2026-06-01',
  status: 'ACTIVE',
});
const year2027 = makeYear({ id: 'year-2', name: '2027-2028', startDate: '2027-06-01' });
const grade8 = makeGrade({ id: 'grade-1', name: 'Grade 8', displayOrder: 8 });
const grade9 = makeGrade({ id: 'grade-2', name: 'Grade 9', displayOrder: 9 });
const section8A = makeSection({ id: 'section-1', name: 'A' });
const section8B = makeSection({ id: 'section-3', name: 'B' });
const section9A = makeSection({
  id: 'section-2',
  name: 'A',
  academicYear: { id: 'year-2', name: '2027-2028' },
  gradeLevel: { id: 'grade-2', name: 'Grade 9' },
});

function renderAdmin(ui: React.ReactElement, route = '/') {
  return renderWithProviders(ui, {
    route,
    auth: { user: makeUser({ id: 'admin-1', roles: ['ROLE_ADMIN'] }) },
  });
}

beforeEach(() => {
  setViewportWidth(1440);
  [...Object.values(people), ...Object.values(academics)].forEach((mock) => mock.mockReset());
  academics.listAcademicYears.mockResolvedValue(pageOf([year2026, year2027]));
  academics.listGrades.mockResolvedValue(pageOf([grade8, grade9]));
  academics.listSections.mockImplementation(({ academicYearId }: { academicYearId?: string }) =>
    Promise.resolve(pageOf(academicYearId === 'year-2' ? [section9A] : [section8A, section8B])),
  );
  academics.listTeacherOptions.mockResolvedValue(pageOf([]));
  people.listPromotions.mockResolvedValue(pageOf([]));
});

describe('StudentsPage', () => {
  it('lists students with their current class', async () => {
    people.listStudents.mockResolvedValue(
      pageOf([
        makeStudent({
          currentEnrollment: {
            enrollmentId: 'e1',
            academicYear: '2026-2027',
            grade: 'Grade 8',
            section: 'A',
            rollNumber: 3,
          },
        }),
        makeStudent({ id: 'student-2', admissionNumber: 'S-101', firstName: 'Ola' }),
      ]),
    );
    renderAdmin(<StudentsPage />);

    expect(await screen.findByRole('cell', { name: 'Grade 8 - A' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Not enrolled' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sam Student' })).toHaveAttribute(
      'href',
      '/admin/students/student-1',
    );
  });

  it('creates a student and sends blank optional fields as null', async () => {
    people.listStudents.mockResolvedValue(pageOf([]));
    people.saveStudent.mockResolvedValue(makeStudent());
    renderAdmin(<StudentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New student' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^admission number/i), 'S-200');
    await userEvent.type(within(dialog).getByLabelText(/^initial password/i), 'Correct-Pass-1!');
    await userEvent.type(within(dialog).getByLabelText(/^first name/i), 'Ola');
    await userEvent.type(within(dialog).getByLabelText(/^last name/i), 'Okoye');
    await userEvent.type(within(dialog).getByLabelText(/^email/i), 'ola@school.test');
    await userEvent.type(within(dialog).getByLabelText(/^date of birth/i), '2012-04-10');
    await userEvent.type(within(dialog).getByLabelText(/^admission date/i), '2026-06-01');
    await userEvent.type(within(dialog).getByLabelText(/^address(?! line)/i), '1 Main Road');
    await userEvent.type(within(dialog).getByLabelText(/^city/i), 'Pune');
    await userEvent.type(within(dialog).getByLabelText(/^emergency contact name/i), 'Parent');
    await userEvent.type(
      within(dialog).getByLabelText(/^emergency contact phone/i),
      '+91 98765 00000',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(people.saveStudent).toHaveBeenCalledWith(undefined, {
        admissionNumber: 'S-200',
        password: 'Correct-Pass-1!',
        firstName: 'Ola',
        lastName: 'Okoye',
        email: 'ola@school.test',
        phoneNumber: null,
        dateOfBirth: '2012-04-10',
        gender: 'MALE',
        admissionDate: '2026-06-01',
        bloodGroup: null,
        addressLine1: '1 Main Road',
        addressLine2: null,
        city: 'Pune',
        state: null,
        postalCode: null,
        emergencyContactName: 'Parent',
        emergencyContactPhone: '+91 98765 00000',
        status: undefined,
        version: undefined,
      }),
    );
  });

  it('validates the form and shows a duplicate admission number from the server', async () => {
    people.listStudents.mockResolvedValue(pageOf([]));
    people.saveStudent.mockRejectedValue(
      new ApiError('Validation failed', {
        status: 400,
        fieldErrors: { admissionNumber: 'Admission number is required' },
      }),
    );
    renderAdmin(<StudentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'New student' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^initial password/i), 'weak');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText('Admission number is required')).toBeInTheDocument();
    expect(within(dialog).getByText('Password must be at least 10 characters')).toBeInTheDocument();
    expect(within(dialog).getByText('First name is required')).toBeInTheDocument();
    expect(people.saveStudent).not.toHaveBeenCalled();
  });

  it('keeps the admission number read-only when editing and sends the version', async () => {
    const student = makeStudent({ version: 4 });
    people.listStudents.mockResolvedValue(pageOf([student]));
    people.saveStudent.mockResolvedValue(student);
    renderAdmin(<StudentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Sam Student' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText('Admission number')).toBeDisabled();
    expect(within(dialog).queryByLabelText(/initial password/i)).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(people.saveStudent).toHaveBeenCalledWith(
        'student-1',
        expect.objectContaining({ version: 4, status: 'ACTIVE', admissionNumber: undefined }),
      ),
    );
  });
});

describe('UsersPage', () => {
  it('suspends another account with its version but offers no actions on your own', async () => {
    const other = makeAdminUser({ version: 3 });
    const me = makeAdminUser({
      id: 'admin-1',
      firstName: 'Ada',
      lastName: 'Admin',
      username: 'admin',
      roles: ['ROLE_ADMIN'],
    });
    people.listUsers.mockResolvedValue(pageOf([me, other]));
    people.changeUserStatus.mockResolvedValue({ ...other, accountStatus: 'SUSPENDED' });
    renderAdmin(<UsersPage />);

    await screen.findByRole('cell', { name: 'Tara Teacher' });
    expect(screen.queryByRole('button', { name: 'Actions for Ada Admin' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Tara Teacher' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Suspend' }));
    const dialog = await screen.findByRole('dialog', { name: /suspend account/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Suspend' }));

    await waitFor(() =>
      expect(people.changeUserStatus).toHaveBeenCalledWith('user-9', 'SUSPENDED', 3),
    );
  });

  it('offers to clear a lockout and to activate a suspended account', async () => {
    people.listUsers.mockResolvedValue(
      pageOf([
        makeAdminUser({ temporarilyLocked: true }),
        makeAdminUser({
          id: 'user-10',
          firstName: 'Sus',
          lastName: 'Pended',
          accountStatus: 'SUSPENDED',
        }),
      ]),
    );
    people.unlockUser.mockResolvedValue(makeAdminUser());
    renderAdmin(<UsersPage />);
    expect(await screen.findByText('Locked out')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Tara Teacher' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Clear lockout' }));
    await waitFor(() => expect(people.unlockUser).toHaveBeenCalledWith('user-9'));

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Sus Pended' }));
    expect(await screen.findByRole('menuitem', { name: 'Activate' })).toBeInTheDocument();
  });
});

describe('EnrollmentsPage', () => {
  beforeEach(() => {
    people.listEnrollments.mockResolvedValue(pageOf([makeEnrollment()]));
  });

  it('shows the active year by default and transfers a student within that year', async () => {
    people.transferEnrollment.mockResolvedValue(makeEnrollment({ id: 'e2' }));
    renderAdmin(<EnrollmentsPage />);
    expect(await screen.findByRole('cell', { name: 'Sam Student (S-100)' })).toBeInTheDocument();
    expect(people.listEnrollments).toHaveBeenCalledWith(
      expect.objectContaining({ academicYearId: 'year-1', status: 'ACTIVE' }),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Actions for Sam Student' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Transfer' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /new section/i }));
    // The student's own section is not offered.
    expect(screen.queryByRole('option', { name: /Grade 8 - A/ })).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('option', { name: /Grade 8 - B/ }));
    await userEvent.type(within(dialog).getByLabelText(/roll number/i), '7');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Transfer' }));

    await waitFor(() =>
      expect(people.transferEnrollment).toHaveBeenCalledWith('enrollment-1', {
        targetSectionId: 'section-3',
        rollNumber: 7,
      }),
    );
  });

  it('shows no actions for enrollments that have ended and explains a full section', async () => {
    people.listEnrollments.mockResolvedValue(pageOf([makeEnrollment({ status: 'PROMOTED' })]));
    renderAdmin(<EnrollmentsPage />);
    await screen.findByRole('cell', { name: 'Sam Student (S-100)' });
    expect(
      screen.queryByRole('button', { name: 'Actions for Sam Student' }),
    ).not.toBeInTheDocument();
  });

  it('reports a business-rule refusal inside the enroll dialog', async () => {
    people.listStudents.mockResolvedValue(pageOf([makeStudent()]));
    people.enrollStudent.mockRejectedValue(
      new ApiError('Grade 8-A (2026-2027) is full (1 seats).', { status: 422 }),
    );
    renderAdmin(<EnrollmentsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Enroll student' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByRole('combobox', { name: /^student/i }), 'Sam');
    await userEvent.click(await screen.findByRole('option', { name: /Sam Student \(S-100\)/ }));
    await userEvent.click(within(dialog).getByRole('combobox', { name: /^section/i }));
    await userEvent.click(await screen.findByRole('option', { name: /Grade 8 - A/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Enroll' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('is full');
    expect(people.enrollStudent).toHaveBeenCalledWith({
      studentId: 'student-1',
      sectionId: 'section-1',
      rollNumber: null,
    });
  });
});

describe('PromotionPage', () => {
  const sam = makeEnrollment();
  const ola = makeEnrollment({
    id: 'enrollment-2',
    student: { id: 'student-2', admissionNumber: 'S-101', name: 'Ola Okoye' },
    section: { id: 'section-3', name: 'B' },
  });

  beforeEach(() => {
    people.listEnrollments.mockResolvedValue(pageOf([sam, ola]));
  });

  it('suggests the next grade, promotes the selected students and reports those that failed', async () => {
    people.promoteStudents.mockResolvedValue({
      batchId: 'b1',
      sourceAcademicYear: '2026-2027',
      targetAcademicYear: '2027-2028',
      performedBy: 'Ada Admin',
      performedAt: '2027-05-01T10:00:00Z',
      total: 2,
      promoted: 1,
      failed: 1,
      results: [
        {
          studentId: 'student-1',
          admissionNumber: 'S-100',
          studentName: 'Sam Student',
          outcome: 'PROMOTED',
          reason: null,
          newEnrollmentId: 'n1',
          rollNumber: 1,
        },
        {
          studentId: 'student-2',
          admissionNumber: 'S-101',
          studentName: 'Ola Okoye',
          outcome: 'FAILED',
          reason: 'Grade 9-A (2027-2028) is full (1 seats).',
          newEnrollmentId: null,
          rollNumber: null,
        },
      ],
    });
    renderAdmin(<PromotionPage />);

    await screen.findByRole('list', { name: 'Students to promote' });
    // The only open section of the next grade is suggested for both students.
    await waitFor(() =>
      expect(screen.getByLabelText('Target section for Sam Student')).toHaveTextContent(
        'Grade 9 - A',
      ),
    );
    expect(screen.getByRole('button', { name: 'Promote 0 selected' })).toBeDisabled();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all on this page (2)' }));
    await userEvent.click(screen.getByRole('button', { name: 'Promote 2 selected' }));
    const dialog = await screen.findByRole('dialog', { name: /promote selected/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Promote' }));

    await waitFor(() =>
      expect(people.promoteStudents).toHaveBeenCalledWith({
        sourceAcademicYearId: 'year-1',
        targetAcademicYearId: 'year-2',
        items: [
          { studentId: 'student-1', targetSectionId: 'section-2' },
          { studentId: 'student-2', targetSectionId: 'section-2' },
        ],
      }),
    );
    const failures = await screen.findByRole('list', { name: 'Students not promoted' });
    expect(within(failures).getByText(/Ola Okoye \(S-101\): Grade 9-A/)).toBeInTheDocument();
    expect(screen.getByText('1 of 2 students promoted to 2027-2028')).toBeInTheDocument();
  });

  it('asks for a later academic year when none exists', async () => {
    academics.listAcademicYears.mockResolvedValue(pageOf([year2026]));
    renderAdmin(<PromotionPage />);

    expect(
      await screen.findByText(/Create or plan a later academic year first/),
    ).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Students to promote' })).not.toBeInTheDocument();
  });
});

describe('GuardiansPage and TeachersPage', () => {
  it('explains why a guardian that is still linked cannot be deleted', async () => {
    people.listGuardians.mockResolvedValue(pageOf([makeGuardian({ studentCount: 1 })]));
    people.deleteGuardian.mockRejectedValue(
      new ApiError('This guardian is linked to students. Unlink them first.', { status: 409 }),
    );
    renderAdmin(<GuardiansPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Maya Parent' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: /delete guardian/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Unlink them first');
  });

  it('keeps the employee ID read-only and saves a changed employment status', async () => {
    const teacher = {
      id: 'teacher-1',
      userId: 'user-9',
      employeeId: 'T-100',
      firstName: 'Tara',
      lastName: 'Teacher',
      email: 'tara@school.test',
      phoneNumber: null,
      joiningDate: '2020-06-01',
      qualification: null,
      specialization: 'Maths',
      employmentStatus: 'ACTIVE' as const,
      accountStatus: 'ACTIVE' as const,
      version: 2,
    };
    people.listTeachers.mockResolvedValue(pageOf([teacher]));
    people.saveTeacher.mockResolvedValue(teacher);
    renderAdmin(<TeachersPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Tara Teacher' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText('Employee ID')).toBeDisabled();
    await userEvent.click(within(dialog).getByRole('combobox', { name: /employment status/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'On leave' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(people.saveTeacher).toHaveBeenCalledWith(
        'teacher-1',
        expect.objectContaining({
          employmentStatus: 'ON_LEAVE',
          specialization: 'Maths',
          qualification: null,
          version: 2,
        }),
      ),
    );
  });
});

describe('StudentDetailPage', () => {
  function renderDetail() {
    return renderAdmin(
      <Routes>
        <Route path="/admin/students/:id" element={<StudentDetailPage />} />
      </Routes>,
      '/admin/students/student-1',
    );
  }

  beforeEach(() => {
    people.getStudent.mockResolvedValue(makeStudent());
    people.listStudentEnrollments.mockResolvedValue([makeEnrollment()]);
    people.listStudentGuardians.mockResolvedValue([makeGuardianLink()]);
  });

  it('shows guardians and the enrollment history, and unlinks a guardian', async () => {
    people.unlinkGuardian.mockResolvedValue(undefined);
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Sam Student' })).toBeInTheDocument();
    expect(await screen.findByText('Maya Parent (Mother)')).toBeInTheDocument();
    expect(screen.getByText('Primary')).toBeInTheDocument();
    const history = screen.getByRole('table', { name: 'Enrollment history' });
    expect(within(history).getByText('Grade 8 - A')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Unlink Maya Parent' }));
    const dialog = await screen.findByRole('dialog', { name: /unlink guardian/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Unlink' }));

    await waitFor(() => expect(people.unlinkGuardian).toHaveBeenCalledWith('student-1', 'link-1'));
  });

  it('links an existing guardian', async () => {
    people.listGuardians.mockResolvedValue(
      pageOf([makeGuardian({ id: 'guardian-2', firstName: 'Ravi' })]),
    );
    people.saveGuardianLink.mockResolvedValue(makeGuardianLink({ id: 'link-2' }));
    renderDetail();
    await screen.findByText('Maya Parent (Mother)');

    await userEvent.click(screen.getByRole('button', { name: 'Link guardian' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('combobox', { name: /^guardian/i }), 'Rav');
    await userEvent.click(await screen.findByRole('option', { name: /Ravi Parent/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Link' }));

    await waitFor(() =>
      expect(people.saveGuardianLink).toHaveBeenCalledWith('student-1', undefined, {
        guardianId: 'guardian-2',
        primaryGuardian: false,
        receivesNotifications: true,
        authorizedPickup: false,
        version: undefined,
      }),
    );
  });
});
