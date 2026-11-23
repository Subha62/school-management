import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { ApiError } from '../../api/errors';
import { formatMoney } from '../../features/fees/labels';
import {
  makeAnnouncement,
  makeClassOption,
  makeCollections,
  makeFeeCategory,
  makeFeeStructure,
  makeFeeSummary,
  makeGrade,
  makePayment,
  makeReceipt,
  makeSection,
  makeStudentFee,
  makeYear,
  pageOf,
} from '../../test/fixtures';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { setViewportWidth } from '../../test/viewport';
import { todayIso } from '../../utils/dates';
import { StudentAnnouncementsPage } from '../student/StudentAnnouncementsPage';
import { StudentFeeDetailPage } from '../student/StudentFeeDetailPage';
import { StudentFeesPage } from '../student/StudentFeesPage';
import { StudentReceiptPage } from '../student/StudentReceiptPage';
import { TeacherAnnouncementsPage } from '../teacher/TeacherAnnouncementsPage';
import { AdminStudentFeesPage } from './AdminStudentFeesPage';
import { AnnouncementsPage } from './AnnouncementsPage';
import { FeeCategoriesPage } from './FeeCategoriesPage';
import { FeeStructuresPage } from './FeeStructuresPage';
import { PaymentsPage } from './PaymentsPage';

const announcements = vi.hoisted(() => ({
  listFeed: vi.fn(),
  getUnreadCount: vi.fn(),
  markRead: vi.fn(),
  listManaged: vi.fn(),
  saveAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
  transitionAnnouncement: vi.fn(),
}));
const fees = vi.hoisted(() => ({
  listCategories: vi.fn(),
  saveCategory: vi.fn(),
  deleteCategory: vi.fn(),
  listStructures: vi.fn(),
  saveStructure: vi.fn(),
  deleteStructure: vi.fn(),
  generateFees: vi.fn(),
  listStudentFees: vi.fn(),
  getStudentFee: vi.fn(),
  recordPayment: vi.fn(),
  waiveFee: vi.fn(),
  unwaiveFee: vi.fn(),
  listPayments: vi.fn(),
  getReceipt: vi.fn(),
  reversePayment: vi.fn(),
  getSummary: vi.fn(),
  getCollections: vi.fn(),
  listMyFees: vi.fn(),
  getMyFee: vi.fn(),
  getMyReceipt: vi.fn(),
}));
const academics = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  listGrades: vi.fn(),
  listSections: vi.fn(),
}));
const operations = vi.hoisted(() => ({ listMyClasses: vi.fn() }));
vi.mock('../../features/announcements/api', () => announcements);
vi.mock('../../features/fees/api', () => fees);
vi.mock('../../features/academics/api', () => academics);
vi.mock('../../features/operations/api', () => operations);

const asAdmin = (ui: React.ReactElement, route = '/admin/announcements') =>
  renderWithProviders(ui, { route, auth: { user: makeUser({ roles: ['ROLE_ADMIN'] }) } });
const asTeacher = (ui: React.ReactElement, route = '/teacher/announcements') =>
  renderWithProviders(ui, { route, auth: { user: makeUser({ roles: ['ROLE_TEACHER'] }) } });
const asStudent = (ui: React.ReactElement, route = '/student/announcements') =>
  renderWithProviders(ui, { route, auth: { user: makeUser() } });
const at = (path: string, element: React.ReactElement) => (
  <Routes>
    <Route path={path} element={element} />
  </Routes>
);

beforeEach(() => {
  setViewportWidth(1440);
  [
    ...Object.values(announcements),
    ...Object.values(fees),
    ...Object.values(academics),
    ...Object.values(operations),
  ].forEach((mock) => mock.mockReset());
  announcements.listFeed.mockResolvedValue(pageOf([]));
  announcements.getUnreadCount.mockResolvedValue({ count: 0 });
  announcements.listManaged.mockResolvedValue(pageOf([]));
  academics.listAcademicYears.mockResolvedValue(pageOf([makeYear({ status: 'ACTIVE' })]));
  academics.listGrades.mockResolvedValue(pageOf([makeGrade()]));
  academics.listSections.mockResolvedValue(pageOf([makeSection()]));
  operations.listMyClasses.mockResolvedValue([
    makeClassOption(),
    makeClassOption({ sectionId: 'section-2', sectionName: 'Grade 7 - B' }),
  ]);
  fees.listCategories.mockResolvedValue(pageOf([makeFeeCategory()]));
  fees.listStructures.mockResolvedValue(pageOf([]));
  fees.listStudentFees.mockResolvedValue(pageOf([]));
  fees.getSummary.mockResolvedValue(makeFeeSummary());
  fees.getCollections.mockResolvedValue(makeCollections());
  fees.listPayments.mockResolvedValue(pageOf([]));
  fees.listMyFees.mockResolvedValue(pageOf([]));
});

const openManageTab = async () =>
  userEvent.click(await screen.findByRole('tab', { name: 'Manage' }));

const chooseOption = async (dialog: HTMLElement, name: RegExp, option: string) => {
  await userEvent.click(within(dialog).getByRole('combobox', { name }));
  await userEvent.click(await screen.findByRole('option', { name: option }));
};

describe('announcement inbox', () => {
  it('marks an announcement read once when it is opened and updates the unread count', async () => {
    announcements.getUnreadCount.mockResolvedValue({ count: 1 });
    announcements.listFeed.mockResolvedValue(pageOf([makeAnnouncement()]));
    announcements.markRead.mockImplementation(async () => {
      announcements.getUnreadCount.mockResolvedValue({ count: 0 });
      announcements.listFeed.mockResolvedValue(pageOf([makeAnnouncement({ read: true })]));
    });
    asStudent(<StudentAnnouncementsPage />);

    expect(await screen.findByText('1 unread')).toBeInTheDocument();
    const summary = await screen.findByRole('button', { name: /Sports day/ });
    expect(screen.getByText('Unread')).toBeInTheDocument();
    await userEvent.click(summary);

    await waitFor(() => expect(announcements.markRead).toHaveBeenCalledWith('ann-1'));
    expect(await screen.findByText('0 unread')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Unread')).not.toBeInTheDocument());
    await userEvent.click(summary);
    await userEvent.click(summary);
    expect(announcements.markRead).toHaveBeenCalledTimes(1);
  });

  it('shows the body as plain text and keeps its line breaks', async () => {
    announcements.listFeed.mockResolvedValue(
      pageOf([makeAnnouncement({ body: 'Line one\n<b>not bold</b>', read: true })]),
    );
    const { container } = asStudent(<StudentAnnouncementsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /Sports day/ }));

    const body = await screen.findByText(/not bold/);
    expect(body).toHaveTextContent('Line one <b>not bold</b>');
    expect(body).toHaveStyle({ whiteSpace: 'pre-wrap' });
    expect(container.querySelector('b')).toBeNull();
  });

  it('asks the server for unread announcements only when the switch is on', async () => {
    announcements.listFeed.mockResolvedValue(pageOf([makeAnnouncement()]));
    asStudent(<StudentAnnouncementsPage />);
    await screen.findByRole('button', { name: /Sports day/ });
    expect(announcements.listFeed).toHaveBeenLastCalledWith({ page: 0, size: 10, unread: false });

    announcements.listFeed.mockResolvedValue(pageOf([]));
    await userEvent.click(screen.getByRole('switch', { name: 'Unread only' }));
    await waitFor(() =>
      expect(announcements.listFeed).toHaveBeenLastCalledWith({ page: 0, size: 10, unread: true }),
    );
    expect(await screen.findByText('No unread announcements')).toBeInTheDocument();
  });

  it('gives students an inbox without a manage tab', async () => {
    announcements.listFeed.mockResolvedValue(pageOf([makeAnnouncement({ read: true })]));
    asStudent(<StudentAnnouncementsPage />);
    expect(await screen.findByRole('heading', { name: 'Announcements' })).toBeInTheDocument();
    await screen.findByRole('button', { name: /Sports day/ });
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /new announcement/i })).not.toBeInTheDocument();
  });

  it('shows an error with a retry when the feed cannot be loaded', async () => {
    announcements.listFeed.mockRejectedValueOnce(new ApiError('Boom', { status: 500 }));
    asStudent(<StudentAnnouncementsPage />);
    expect(await screen.findByText('Boom')).toBeInTheDocument();
    announcements.listFeed.mockResolvedValue(pageOf([makeAnnouncement({ read: true })]));
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('button', { name: /Sports day/ })).toBeInTheDocument();
  });
});

describe('announcement management', () => {
  it('publishes a draft only after the confirmation', async () => {
    announcements.listManaged.mockResolvedValue(
      pageOf([
        makeAnnouncement({ title: 'Draft one', status: 'DRAFT', live: false, publishedAt: null }),
      ]),
    );
    announcements.transitionAnnouncement.mockResolvedValue(makeAnnouncement());
    asAdmin(<AnnouncementsPage />);
    await openManageTab();

    expect(await screen.findByText('Draft')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Draft one' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Publish' }));
    const dialog = await screen.findByRole('dialog');
    expect(announcements.transitionAnnouncement).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Publish' }));
    await waitFor(() =>
      expect(announcements.transitionAnnouncement).toHaveBeenCalledWith(
        'admin',
        'ann-1',
        'publish',
      ),
    );
    expect(await screen.findByText('Announcement published')).toBeInTheDocument();
  });

  it('labels published announcements as scheduled, live or expired', async () => {
    announcements.listManaged.mockResolvedValue(
      pageOf([
        makeAnnouncement({
          id: 'a',
          title: 'Soon',
          live: false,
          publishAt: '2999-01-01T00:00:00Z',
        }),
        makeAnnouncement({ id: 'b', title: 'Now', readCount: 4 }),
        makeAnnouncement({ id: 'c', title: 'Old', live: false, expiresAt: '2020-01-01T00:00:00Z' }),
      ]),
    );
    asAdmin(<AnnouncementsPage />);
    await openManageTab();

    expect(
      await within(await screen.findByRole('row', { name: /Soon/ })).findByText('Scheduled'),
    ).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: /Now/ })).getByText('Live')).toBeInTheDocument();
    expect(
      within(screen.getByRole('row', { name: /Old/ })).getByText('Expired'),
    ).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: /Now/ })).getByText('4')).toBeInTheDocument();
  });

  it('needs a class for the class audience and only shows the class select for it', async () => {
    announcements.saveAnnouncement.mockResolvedValue(makeAnnouncement());
    asAdmin(<AnnouncementsPage />);
    await openManageTab();
    await userEvent.click(await screen.findByRole('button', { name: /new announcement/i }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^title/i), 'Parents meeting');
    await userEvent.type(within(dialog).getByLabelText(/^message/i), 'Friday at 5');

    expect(within(dialog).queryByRole('combobox', { name: /^class/i })).not.toBeInTheDocument();
    await chooseOption(dialog, /audience/i, 'One class');
    expect(within(dialog).getByRole('combobox', { name: /^class/i })).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    expect(await within(dialog).findByText('Choose a class')).toBeInTheDocument();
    expect(announcements.saveAnnouncement).not.toHaveBeenCalled();

    await chooseOption(dialog, /^class/i, 'Grade 8 - A (2026-2027)');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(announcements.saveAnnouncement).toHaveBeenCalled());
    expect(announcements.saveAnnouncement.mock.calls[0]).toEqual([
      'admin',
      undefined,
      {
        title: 'Parents meeting',
        body: 'Friday at 5',
        audience: 'SECTION',
        sectionId: 'section-1',
        publishAt: null,
        expiresAt: null,
        version: undefined,
      },
    ]);
  });

  it('does not send a class for the other audiences', async () => {
    announcements.saveAnnouncement.mockResolvedValue(makeAnnouncement());
    asAdmin(<AnnouncementsPage />);
    await openManageTab();
    await userEvent.click(await screen.findByRole('button', { name: /new announcement/i }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^title/i), 'Holiday');
    await userEvent.type(within(dialog).getByLabelText(/^message/i), 'School is closed');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));

    await waitFor(() => expect(announcements.saveAnnouncement).toHaveBeenCalled());
    const body = announcements.saveAnnouncement.mock.calls[0]?.[2];
    expect(body).toMatchObject({ audience: 'ALL' });
    expect(body).not.toHaveProperty('sectionId');
  });

  it('rejects an expiry that is before the start', async () => {
    asAdmin(<AnnouncementsPage />);
    await openManageTab();
    await userEvent.click(await screen.findByRole('button', { name: /new announcement/i }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^title/i), 'Exam notice');
    await userEvent.type(within(dialog).getByLabelText(/^message/i), 'Bring a pen');
    fireEvent.change(within(dialog).getByLabelText(/show from/i), {
      target: { value: '2026-12-02T10:00' },
    });
    fireEvent.change(within(dialog).getByLabelText(/expires at/i), {
      target: { value: '2026-12-01T10:00' },
    });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save draft' }));

    expect(
      await within(dialog).findByText('The expiry must be after the start'),
    ).toBeInTheDocument();
    expect(announcements.saveAnnouncement).not.toHaveBeenCalled();
  });

  it('lets a teacher address only the classes they teach', async () => {
    asTeacher(<TeacherAnnouncementsPage />);
    await openManageTab();
    await waitFor(() => expect(announcements.listManaged).toHaveBeenCalled());
    expect(announcements.listManaged.mock.calls[0]?.[0]).toBe('teacher');
    await userEvent.click(await screen.findByRole('button', { name: /new announcement/i }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(await within(dialog).findByRole('combobox', { name: /^class/i }));
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(options).toEqual(['Grade 8 - A', 'Grade 7 - B']);
    expect(academics.listSections).not.toHaveBeenCalled();
    await userEvent.keyboard('{Escape}');

    await userEvent.click(within(dialog).getByRole('combobox', { name: /audience/i }));
    expect((await screen.findAllByRole('option')).map((o) => o.textContent)).toEqual(['One class']);
  });

  it('fixes the audience of a published announcement while it is edited', async () => {
    announcements.listManaged.mockResolvedValue(pageOf([makeAnnouncement({ title: 'Live one' })]));
    announcements.saveAnnouncement.mockResolvedValue(makeAnnouncement());
    asAdmin(<AnnouncementsPage />);
    await openManageTab();
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for Live one' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByRole('combobox', { name: /audience/i })).toHaveAttribute(
      'aria-disabled',
      'true',
    );
    expect(within(dialog).getByText(/keeps its audience/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(announcements.saveAnnouncement).toHaveBeenCalled());
    expect(announcements.saveAnnouncement.mock.calls[0]?.[1]).toBe('ann-1');
    expect(announcements.saveAnnouncement.mock.calls[0]?.[2]).toMatchObject({ version: 0 });
  });
});

describe('FeeCategoriesPage', () => {
  it('creates a category', async () => {
    fees.listCategories.mockResolvedValue(pageOf([]));
    fees.saveCategory.mockResolvedValue(makeFeeCategory());
    asAdmin(<FeeCategoriesPage />, '/admin/fee-categories');
    await userEvent.click(await screen.findByRole('button', { name: 'New category' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));
    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/^name/i), 'Transport');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create category' }));
    await waitFor(() =>
      expect(fees.saveCategory).toHaveBeenCalledWith(undefined, {
        name: 'Transport',
        description: null,
        active: true,
        version: undefined,
      }),
    );
  });
});

describe('FeeStructuresPage', () => {
  const render = () => asAdmin(<FeeStructuresPage />, '/admin/fee-structures');

  it('creates a structure with the amount as a number', async () => {
    fees.saveStructure.mockResolvedValue(makeFeeStructure());
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'New fee structure' }));
    const dialog = await screen.findByRole('dialog');
    await chooseOption(dialog, /academic year/i, '2026-2027');
    await chooseOption(dialog, /^grade/i, 'Grade 8');
    await chooseOption(dialog, /fee category/i, 'Tuition');
    await userEvent.type(within(dialog).getByLabelText(/^amount/i), '5000.50');
    fireEvent.change(within(dialog).getByLabelText(/due date/i), {
      target: { value: '2026-12-01' },
    });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create structure' }));

    await waitFor(() => expect(fees.saveStructure).toHaveBeenCalled());
    expect(fees.saveStructure.mock.calls[0]).toEqual([
      undefined,
      {
        academicYearId: 'year-1',
        gradeLevelId: 'grade-1',
        feeCategoryId: 'cat-1',
        description: null,
        amount: 5000.5,
        dueDate: '2026-12-01',
        active: true,
        version: undefined,
      },
    ]);
  });

  it('will not accept a zero or negative amount', async () => {
    render();
    await userEvent.click(await screen.findByRole('button', { name: 'New fee structure' }));
    const dialog = await screen.findByRole('dialog');
    await chooseOption(dialog, /academic year/i, '2026-2027');
    await chooseOption(dialog, /^grade/i, 'Grade 8');
    await chooseOption(dialog, /fee category/i, 'Tuition');
    await userEvent.type(within(dialog).getByLabelText(/^amount/i), '0');
    fireEvent.change(within(dialog).getByLabelText(/due date/i), {
      target: { value: '2026-12-01' },
    });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create structure' }));

    expect(await within(dialog).findByText('Amount must be more than 0')).toBeInTheDocument();
    expect(fees.saveStructure).not.toHaveBeenCalled();
  });

  it('freezes the amount and due date after fees were generated and blocks deletion', async () => {
    fees.listStructures.mockResolvedValue(pageOf([makeFeeStructure({ billedStudents: 12 })]));
    fees.saveStructure.mockResolvedValue(makeFeeStructure());
    render();
    await userEvent.click(await screen.findByRole('button', { name: /Actions for Tuition/ }));
    expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    expect(within(dialog).getByLabelText(/^amount/i)).toBeDisabled();
    expect(within(dialog).getByLabelText(/due date/i)).toBeDisabled();
    expect(
      within(dialog).getByText(/Fees were already generated for 12 students/),
    ).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(fees.saveStructure).toHaveBeenCalled());
    expect(fees.saveStructure.mock.calls[0]).toEqual([
      'fs-1',
      {
        description: 'Term 1',
        amount: 5000,
        dueDate: '2026-12-01',
        active: true,
        version: 0,
      },
    ]);
  });

  it('generates fees only after the confirmation and reports the result', async () => {
    fees.listStructures.mockResolvedValue(pageOf([makeFeeStructure()]));
    fees.generateFees.mockResolvedValue({ created: 20, alreadyBilled: 5 });
    render();
    await userEvent.click(await screen.findByRole('button', { name: /Actions for Tuition/ }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Generate fees' }));
    const dialog = await screen.findByRole('dialog');
    expect(fees.generateFees).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Generate fees' }));

    await waitFor(() => expect(fees.generateFees).toHaveBeenCalledWith('fs-1'));
    expect(
      await screen.findByText('Fees generated: 20 created, 5 already billed'),
    ).toBeInTheDocument();
  });

  it('shows how many students are billed', async () => {
    fees.listStructures.mockResolvedValue(pageOf([makeFeeStructure({ billedStudents: 12 })]));
    render();
    const row = await screen.findByRole('row', { name: /Tuition/ });
    expect(within(row).getByText('12')).toBeInTheDocument();
    expect(within(row).getByText(formatMoney(5000))).toBeInTheDocument();
  });
});

describe('AdminStudentFeesPage', () => {
  const render = () => asAdmin(<AdminStudentFeesPage />, '/admin/student-fees');
  const openPayment = async () => {
    await userEvent.click(
      await screen.findByRole('button', { name: 'Actions for Sam Student Tuition' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Record payment' }));
    return screen.findByRole('dialog');
  };

  beforeEach(() => {
    fees.listStudentFees.mockResolvedValue(pageOf([makeStudentFee()]));
  });

  it('shows the summary and fees of the active year and filters by status', async () => {
    render();
    const summary = await screen.findByLabelText('Fee summary');
    expect(within(summary).getByText(formatMoney(15000))).toBeInTheDocument();
    expect(fees.getSummary).toHaveBeenCalledWith('year-1', undefined);
    expect(fees.listStudentFees).toHaveBeenCalledWith(
      expect.objectContaining({ academicYearId: 'year-1', status: undefined }),
    );
    const row = await screen.findByRole('row', { name: /Sam Student/ });
    expect(within(row).getByText(formatMoney(1500))).toBeInTheDocument();
    expect(within(row).getByText('Partially paid')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Overdue' }));
    await waitFor(() =>
      expect(fees.listStudentFees).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'OVERDUE' }),
      ),
    );
  });

  it('records a payment that defaults to the balance and refreshes the fees and summary', async () => {
    fees.recordPayment.mockResolvedValue(makePayment());
    render();
    const dialog = await openPayment();
    expect(within(dialog).getByLabelText(/^amount/i)).toHaveValue('1500.00');
    expect(within(dialog).getByLabelText(/payment date/i)).toHaveValue(todayIso());
    const feeCalls = fees.listStudentFees.mock.calls.length;
    const summaryCalls = fees.getSummary.mock.calls.length;

    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    await waitFor(() =>
      expect(fees.recordPayment).toHaveBeenCalledWith('fee-1', {
        amount: 1500,
        method: 'CASH',
        reference: null,
        paidOn: todayIso(),
      }),
    );
    expect(await screen.findByText(/Payment recorded/)).toBeInTheDocument();
    await waitFor(() => expect(fees.listStudentFees.mock.calls.length).toBeGreaterThan(feeCalls));
    expect(fees.getSummary.mock.calls.length).toBeGreaterThan(summaryCalls);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('accepts only positive amounts with at most two decimals', async () => {
    render();
    const dialog = await openPayment();
    const amount = within(dialog).getByLabelText(/^amount/i);
    await userEvent.clear(amount);
    await userEvent.type(amount, '0');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    expect(await within(dialog).findByText('Amount must be more than 0')).toBeInTheDocument();

    await userEvent.clear(amount);
    await userEvent.type(amount, '10.555');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    expect(
      await within(dialog).findByText('Use a number with at most two decimal places'),
    ).toBeInTheDocument();
    expect(fees.recordPayment).not.toHaveBeenCalled();
  });

  it('shows an over-balance refusal inside the dialog and keeps it open', async () => {
    fees.recordPayment.mockRejectedValue(
      new ApiError('The payment is more than the balance of this fee.', { status: 422 }),
    );
    render();
    const dialog = await openPayment();
    const amount = within(dialog).getByLabelText(/^amount/i);
    await userEvent.clear(amount);
    await userEvent.type(amount, '9999');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('more than the balance');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows a server error about the payment date next to the field', async () => {
    fees.recordPayment.mockRejectedValue(
      new ApiError('Bad request', {
        status: 400,
        fieldErrors: { paidOn: 'Date is in the future' },
      }),
    );
    render();
    const dialog = await openPayment();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }));
    expect(await within(dialog).findByText('Date is in the future')).toBeInTheDocument();
  });

  it('needs a reason to waive a fee', async () => {
    fees.waiveFee.mockResolvedValue(makeStudentFee({ status: 'WAIVED' }));
    render();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Actions for Sam Student Tuition' }),
    );
    await userEvent.click(screen.getByRole('menuitem', { name: 'Waive' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive fee' }));
    expect(await within(dialog).findByText('A reason is required')).toBeInTheDocument();
    await userEvent.type(within(dialog).getByLabelText(/^reason/i), 'abc');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive fee' }));
    expect(
      await within(dialog).findByText('The reason must be at least 5 characters'),
    ).toBeInTheDocument();
    expect(fees.waiveFee).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/^reason/i), 'de scholarship');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Waive fee' }));
    await waitFor(() => expect(fees.waiveFee).toHaveBeenCalledWith('fee-1', 'abcde scholarship'));
  });

  it('offers to remove the waiver of a waived fee instead of a payment', async () => {
    fees.listStudentFees.mockResolvedValue(
      pageOf([makeStudentFee({ status: 'WAIVED', balance: 0, waiveReason: 'Scholarship' })]),
    );
    render();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Actions for Sam Student Tuition' }),
    );
    expect(screen.getByRole('menuitem', { name: 'Remove waiver' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Record payment' })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Waive' })).not.toBeInTheDocument();
  });
});

describe('PaymentsPage', () => {
  it('shows collection totals and reverses a payment with a reason', async () => {
    fees.listPayments.mockResolvedValue(pageOf([makePayment()]));
    fees.reversePayment.mockImplementation(async () => {
      fees.listPayments.mockResolvedValue(
        pageOf([makePayment({ reversed: true, reversalReason: 'Entered twice' })]),
      );
      return makePayment({ reversed: true });
    });
    asAdmin(<PaymentsPage />, '/admin/payments');

    const totals = await screen.findByLabelText('Collections');
    expect(within(totals).getByText(formatMoney(18000))).toBeInTheDocument();
    const row = await screen.findByRole('row', { name: /RCPT-0001/ });
    expect(within(row).queryByText('Reversed')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Actions for RCPT-0001' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Reverse' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reverse payment' }));
    expect(await within(dialog).findByText('A reason is required')).toBeInTheDocument();
    expect(fees.reversePayment).not.toHaveBeenCalled();

    await userEvent.type(within(dialog).getByLabelText(/^reason/i), 'Entered twice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reverse payment' }));
    await waitFor(() => expect(fees.reversePayment).toHaveBeenCalledWith('pay-1', 'Entered twice'));

    const reversedRow = await screen.findByRole('row', { name: /RCPT-0001/ });
    expect(await within(reversedRow).findByText('Reversed')).toBeInTheDocument();
    expect(within(reversedRow).getByText(formatMoney(3500))).toHaveStyle({
      textDecoration: 'line-through',
    });
    await userEvent.click(screen.getByRole('button', { name: 'Actions for RCPT-0001' }));
    expect(screen.getByRole('menuitem', { name: 'Receipt' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Reverse' })).not.toBeInTheDocument();
  });

  it('shows a reversal refused by the server inside the dialog', async () => {
    fees.listPayments.mockResolvedValue(pageOf([makePayment()]));
    fees.reversePayment.mockRejectedValue(
      new ApiError('This payment was already reversed.', { status: 409 }),
    );
    asAdmin(<PaymentsPage />, '/admin/payments');
    await userEvent.click(await screen.findByRole('button', { name: 'Actions for RCPT-0001' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Reverse' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^reason/i), 'Entered twice');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reverse payment' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already reversed');
  });

  it('filters by method and reversal and ignores an invalid date range', async () => {
    asAdmin(<PaymentsPage />, '/admin/payments');
    await screen.findByText('No payments found');
    await userEvent.click(screen.getByRole('combobox', { name: 'Reversal' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Reversed only' }));
    await waitFor(() =>
      expect(fees.listPayments).toHaveBeenLastCalledWith(
        expect.objectContaining({ reversed: true }),
      ),
    );

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-12-10' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-12-01' } });
    expect(
      await screen.findByText('The end date cannot be before the start date'),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(fees.listPayments).toHaveBeenLastCalledWith(
        expect.objectContaining({ from: undefined, to: undefined }),
      ),
    );
  });
});

describe('student fees', () => {
  it('lists only the student\u2019s own fees with the balance and overdue emphasis', async () => {
    fees.listMyFees.mockResolvedValue(pageOf([makeStudentFee({ status: 'OVERDUE' })]));
    asStudent(<StudentFeesPage />, '/student/fees');

    const row = await screen.findByRole('row', { name: /Tuition/ });
    expect(within(row).getByText(formatMoney(1500))).toBeInTheDocument();
    expect(within(row).getByText('Overdue')).toBeInTheDocument();
    expect(fees.listMyFees).toHaveBeenCalledWith({ page: 0, size: 20, status: undefined });
    expect(fees.listStudentFees).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Paid' }));
    await waitFor(() =>
      expect(fees.listMyFees).toHaveBeenLastCalledWith({ page: 0, size: 20, status: 'PAID' }),
    );
  });

  it('uses cards on a phone', async () => {
    setViewportWidth(375);
    fees.listMyFees.mockResolvedValue(pageOf([makeStudentFee()]));
    asStudent(<StudentFeesPage />, '/student/fees');
    expect(await screen.findAllByRole('listitem')).toHaveLength(1);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows the payments of a fee, marks reversed ones and links to the receipts', async () => {
    fees.getMyFee.mockResolvedValue({
      fee: makeStudentFee(),
      payments: [
        makePayment(),
        makePayment({
          id: 'pay-2',
          receiptNumber: 'RCPT-0002',
          reversed: true,
          amount: 100,
          reversalReason: 'Entered twice',
        }),
      ],
    });
    asStudent(at('/student/fees/:id', <StudentFeeDetailPage />), '/student/fees/fee-1');

    const link = await screen.findByRole('link', { name: 'Receipt RCPT-0001' });
    expect(link).toHaveAttribute('href', '/student/fees/fee-1/receipts/pay-1');
    expect(screen.getByRole('link', { name: 'Receipt RCPT-0002' })).toBeInTheDocument();
    expect(screen.getAllByText('Reversed')).toHaveLength(1);
    expect(screen.getByText(/Entered twice/)).toBeInTheDocument();
    expect(fees.getMyFee).toHaveBeenCalledWith('fee-1');
  });

  it('renders a receipt and a clear banner when the payment was reversed', async () => {
    fees.getMyReceipt.mockResolvedValue(
      makeReceipt({
        reversed: true,
        reversalReason: 'Entered twice',
        reversedAt: '2026-11-12T10:00:00Z',
      }),
    );
    asStudent(
      at('/student/fees/:id/receipts/:paymentId', <StudentReceiptPage />),
      '/student/fees/fee-1/receipts/pay-1',
    );

    const receipt = await screen.findByRole('article', { name: 'Receipt RCPT-0001' });
    expect(fees.getMyReceipt).toHaveBeenCalledWith('pay-1');
    expect(within(receipt).getByText('Demo School')).toBeInTheDocument();
    expect(within(receipt).getByText('REVERSED')).toBeInTheDocument();
    expect(within(receipt).getByText(/Reason: Entered twice/)).toBeInTheDocument();
    expect(within(receipt).getByText(formatMoney(3500))).toHaveStyle({
      textDecoration: 'line-through',
    });
    expect(within(receipt).getByText(formatMoney(1500))).toBeInTheDocument();
    expect(within(receipt).getByText('UPI-778')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Print' })).toBeInTheDocument();
  });

  it('does not show a reversed banner on a valid receipt', async () => {
    fees.getMyReceipt.mockResolvedValue(makeReceipt());
    asStudent(
      at('/student/fees/:id/receipts/:paymentId', <StudentReceiptPage />),
      '/student/fees/fee-1/receipts/pay-1',
    );
    await screen.findByRole('article', { name: 'Receipt RCPT-0001' });
    expect(screen.queryByText('REVERSED')).not.toBeInTheDocument();
  });
});
