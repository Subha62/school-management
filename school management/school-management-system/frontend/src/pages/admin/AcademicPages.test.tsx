import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../../api/errors';
import {
  makeAssignment,
  makeGrade,
  makeSection,
  makeSubject,
  makeTeacher,
  makeYear,
  pageOf,
} from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import { GradesPage } from './GradesPage';
import { SectionsPage } from './SectionsPage';
import { SubjectsPage } from './SubjectsPage';
import { TeacherAssignmentsPage } from './TeacherAssignmentsPage';

const api = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  listGrades: vi.fn(),
  saveGrade: vi.fn(),
  deleteGrade: vi.fn(),
  listSubjects: vi.fn(),
  saveSubject: vi.fn(),
  deleteSubject: vi.fn(),
  listSections: vi.fn(),
  saveSection: vi.fn(),
  deleteSection: vi.fn(),
  listAssignments: vi.fn(),
  saveAssignment: vi.fn(),
  deleteAssignment: vi.fn(),
  listTeacherOptions: vi.fn(),
}));
vi.mock('../../features/academics/api', () => api);

const activeYear = makeYear({ id: 'year-1', name: '2026-2027', status: 'ACTIVE' });
const grade = makeGrade();
const math = makeSubject();
const tara = makeTeacher();

function renderAdmin(ui: React.ReactElement) {
  return renderWithProviders(ui, { auth: { user: makeUser({ roles: ['ROLE_ADMIN'] }) } });
}

beforeEach(() => {
  setViewportWidth(1440);
  Object.values(api).forEach((mock) => mock.mockReset());
  api.listAcademicYears.mockResolvedValue(pageOf([activeYear]));
  api.listGrades.mockResolvedValue(pageOf([grade]));
  api.listSubjects.mockResolvedValue(pageOf([math]));
  api.listSections.mockResolvedValue(pageOf([makeSection()]));
  api.listAssignments.mockResolvedValue(pageOf([]));
  api.listTeacherOptions.mockResolvedValue(pageOf([tara]));
});

describe('GradesPage', () => {
  it('lists grades and creates one with a numeric display order', async () => {
    api.listGrades.mockResolvedValue(pageOf([grade]));
    api.saveGrade.mockResolvedValue(makeGrade({ id: 'g2' }));
    renderAdmin(<GradesPage />);
    expect(await screen.findByRole('cell', { name: 'Grade 8' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'New grade' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Grade 9');
    await userEvent.clear(within(dialog).getByLabelText(/display order/i));
    await userEvent.type(within(dialog).getByLabelText(/display order/i), '9');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(api.saveGrade).toHaveBeenCalledWith(undefined, {
        name: 'Grade 9',
        displayOrder: 9,
        active: true,
        version: undefined,
      }),
    );
  });

  it('explains why a grade that sections use cannot be deleted', async () => {
    api.deleteGrade.mockRejectedValue(
      new ApiError('This grade is used by sections. Mark it inactive instead of deleting it.', {
        status: 409,
      }),
    );
    renderAdmin(<GradesPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Grade 8' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: /delete grade/i });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Mark it inactive');
  });
});

describe('SubjectsPage', () => {
  it('shows a server-side validation error next to the code field', async () => {
    api.saveSubject.mockRejectedValue(
      new ApiError('Validation failed', {
        status: 400,
        fieldErrors: { code: 'Code may contain letters, digits, dots, dashes and underscores' },
      }),
    );
    renderAdmin(<SubjectsPage />);
    await screen.findByRole('cell', { name: 'MATH' });
    await userEvent.click(screen.getByRole('button', { name: 'New subject' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^code/i), 'OK1');
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Something');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText(/code may contain letters/i)).toBeInTheDocument();
  });

  it('rejects an invalid code on the client without calling the API', async () => {
    renderAdmin(<SubjectsPage />);
    await screen.findByRole('cell', { name: 'MATH' });
    await userEvent.click(screen.getByRole('button', { name: 'New subject' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^code/i), 'bad code');
    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Something');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByText(/use letters, digits/i)).toBeInTheDocument();
    expect(api.saveSubject).not.toHaveBeenCalled();
  });
});

describe('SectionsPage', () => {
  it('opens on the active academic year and lists its sections with the class teacher', async () => {
    api.listSections.mockResolvedValue(
      pageOf([makeSection({ classTeacher: { id: 'teacher-1', name: 'Tara Teacher' } })]),
    );
    renderAdmin(<SectionsPage />);

    expect(await screen.findByRole('cell', { name: 'Grade 8 - A' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Tara Teacher' })).toBeInTheDocument();
    await waitFor(() =>
      expect(api.listSections).toHaveBeenCalledWith(
        expect.objectContaining({ academicYearId: 'year-1' }),
      ),
    );
  });

  it('creates a section with a class teacher chosen by searching', async () => {
    api.saveSection.mockResolvedValue(makeSection({ id: 'new' }));
    renderAdmin(<SectionsPage />);
    await screen.findByRole('cell', { name: 'Grade 8 - A' });
    await userEvent.click(screen.getByRole('button', { name: 'New section' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('combobox', { name: /grade/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8' }));
    await userEvent.type(within(dialog).getByLabelText(/section name/i), 'B');
    await userEvent.type(within(dialog).getByRole('combobox', { name: /class teacher/i }), 'Tara');
    await userEvent.click(await screen.findByRole('option', { name: /Tara Teacher/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(api.saveSection).toHaveBeenCalledWith(undefined, {
        academicYearId: 'year-1',
        gradeLevelId: 'grade-1',
        name: 'B',
        capacity: 40,
        classTeacherId: 'teacher-1',
        active: true,
        version: undefined,
      }),
    );
    expect(await screen.findByText('Section created')).toBeInTheDocument();
  });

  it('requires a grade and shows the duplicate-name conflict from the server', async () => {
    api.saveSection.mockRejectedValue(
      new ApiError('Section A already exists for Grade 8 in 2026-2027.', { status: 409 }),
    );
    renderAdmin(<SectionsPage />);
    await screen.findByRole('cell', { name: 'Grade 8 - A' });
    await userEvent.click(screen.getByRole('button', { name: 'New section' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/section name/i), 'A');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText('Grade is required')).toBeInTheDocument();
    expect(api.saveSection).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('combobox', { name: /grade/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already exists');
  });

  it('does not allow changing the year or grade of an existing section', async () => {
    renderAdmin(<SectionsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Grade 8 A' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByRole('combobox', { name: /academic year/i })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
    expect(within(dialog).getByRole('combobox', { name: /grade/i })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  it('offers no actions for sections of a closed year', async () => {
    api.listAcademicYears.mockResolvedValue(pageOf([makeYear({ id: 'year-1', status: 'CLOSED' })]));
    renderAdmin(<SectionsPage />);

    expect(await screen.findByRole('cell', { name: 'Grade 8 - A' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /actions for/i })).not.toBeInTheDocument();
  });
});

describe('TeacherAssignmentsPage', () => {
  async function chooseSection() {
    await userEvent.click(await screen.findByRole('combobox', { name: /^section/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Grade 8 - A' }));
  }

  it('only allows assigning a subject once a section is chosen', async () => {
    renderAdmin(<TeacherAssignmentsPage />);
    const assign = await screen.findByRole('button', { name: 'Assign subject' });
    expect(assign).toBeDisabled();

    await chooseSection();

    await waitFor(() => expect(assign).toBeEnabled());
    await waitFor(() =>
      expect(api.listAssignments).toHaveBeenLastCalledWith(
        expect.objectContaining({ sectionId: 'section-1', academicYearId: 'year-1' }),
      ),
    );
  });

  it('assigns a subject with periods and no teacher yet', async () => {
    api.saveAssignment.mockResolvedValue(makeAssignment());
    renderAdmin(<TeacherAssignmentsPage />);
    await chooseSection();
    await userEvent.click(screen.getByRole('button', { name: 'Assign subject' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('combobox', { name: /subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'MATH - Mathematics' }));
    await userEvent.clear(within(dialog).getByLabelText(/weekly periods/i));
    await userEvent.type(within(dialog).getByLabelText(/weekly periods/i), '6');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign' }));

    await waitFor(() =>
      expect(api.saveAssignment).toHaveBeenCalledWith(undefined, {
        sectionId: 'section-1',
        subjectId: 'subject-1',
        teacherId: null,
        weeklyPeriods: 6,
        active: true,
        version: undefined,
      }),
    );
  });

  it('shows who teaches what and flags subjects without a teacher', async () => {
    api.listAssignments.mockResolvedValue(
      pageOf([
        makeAssignment({ teacher: { id: 'teacher-1', name: 'Tara Teacher' } }),
        makeAssignment({
          id: 'a2',
          subject: { id: 's2', code: 'ENG', name: 'English' },
          teacher: null,
        }),
      ]),
    );
    renderAdmin(<TeacherAssignmentsPage />);

    expect(await screen.findByRole('cell', { name: 'Tara Teacher' })).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('shows the server reason when a subject is already assigned', async () => {
    api.saveAssignment.mockRejectedValue(
      new ApiError('Mathematics is already assigned to this section.', { status: 409 }),
    );
    renderAdmin(<TeacherAssignmentsPage />);
    await chooseSection();
    await userEvent.click(screen.getByRole('button', { name: 'Assign subject' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('combobox', { name: /subject/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'MATH - Mathematics' }));

    await userEvent.click(within(dialog).getByRole('button', { name: 'Assign' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already assigned');
  });

  it('removes an assignment only after confirmation', async () => {
    api.listAssignments.mockResolvedValue(pageOf([makeAssignment()]));
    api.deleteAssignment.mockResolvedValue(undefined);
    renderAdmin(<TeacherAssignmentsPage />);
    await userEvent.click(
      await screen.findByRole('button', { name: /actions for mathematics in grade 8 a/i }),
    );
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Remove' }));
    expect(api.deleteAssignment).not.toHaveBeenCalled();

    const dialog = await screen.findByRole('dialog', { name: /remove subject/i });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Remove' }));

    await waitFor(() => expect(api.deleteAssignment).toHaveBeenCalledWith('assignment-1'));
  });

  it('is read-only for a closed academic year', async () => {
    api.listAcademicYears.mockResolvedValue(pageOf([makeYear({ id: 'year-1', status: 'CLOSED' })]));
    api.listAssignments.mockResolvedValue(pageOf([makeAssignment()]));
    renderAdmin(<TeacherAssignmentsPage />);

    expect(
      await screen.findByText(/is closed, so its assignments are read-only/i),
    ).toBeInTheDocument();
    expect(await screen.findByRole('cell', { name: 'MATH - Mathematics' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /actions for/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Assign subject' })).toBeDisabled();
  });
});
