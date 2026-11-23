import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../../api/errors';
import { makeUser, renderWithProviders } from '../../test/renderWithProviders';
import { makeYear, pageOf } from '../../test/fixtures';
import { setViewportWidth } from '../../test/viewport';
import { AcademicYearsPage } from './AcademicYearsPage';

const api = vi.hoisted(() => ({
  listAcademicYears: vi.fn(),
  saveAcademicYear: vi.fn(),
  activateAcademicYear: vi.fn(),
  closeAcademicYear: vi.fn(),
  deleteAcademicYear: vi.fn(),
}));
vi.mock('../../features/academics/api', () => api);

const planned = makeYear({ id: 'y-planned', name: '2027-2028', status: 'PLANNED' });
const active = makeYear({ id: 'y-active', name: '2026-2027', status: 'ACTIVE' });
const closed = makeYear({ id: 'y-closed', name: '2025-2026', status: 'CLOSED' });

function renderPage() {
  return renderWithProviders(<AcademicYearsPage />, {
    route: '/admin/academic-years',
    auth: { user: makeUser({ roles: ['ROLE_ADMIN'] }) },
  });
}

async function openActions(name: string) {
  await userEvent.click(await screen.findByRole('button', { name: `Actions for ${name}` }));
}

beforeEach(() => {
  setViewportWidth(1440);
  Object.values(api).forEach((mock) => mock.mockReset());
  api.listAcademicYears.mockResolvedValue(pageOf([planned, active, closed]));
});

describe('AcademicYearsPage', () => {
  it('lists the years with their status', async () => {
    renderPage();

    expect(await screen.findByRole('cell', { name: '2027-2028' })).toBeInTheDocument();
    expect(screen.getByText('Planned')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Closed')).toBeInTheDocument();
  });

  it('offers only the actions that fit each status', async () => {
    renderPage();

    await openActions('2027-2028');
    expect(await screen.findAllByRole('menuitem')).toHaveLength(3);
    expect(screen.getByRole('menuitem', { name: 'Activate' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');

    await openActions('2026-2027');
    expect(await screen.findByRole('menuitem', { name: 'Close year' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Activate' })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument();
    await userEvent.keyboard('{Escape}');

    // A closed year is read-only: it has no actions at all.
    expect(screen.queryByRole('button', { name: 'Actions for 2025-2026' })).not.toBeInTheDocument();
  });

  it('validates the new academic year before calling the API', async () => {
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'New academic year' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^name/i), '2028-2029');
    await userEvent.type(within(dialog).getByLabelText(/start date/i), '2028-06-01');
    await userEvent.type(within(dialog).getByLabelText(/end date/i), '2028-05-01');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(
      await within(dialog).findByText('End date must be after the start date'),
    ).toBeInTheDocument();
    expect(api.saveAcademicYear).not.toHaveBeenCalled();
  });

  it('creates an academic year, closes the dialog and confirms with a notification', async () => {
    api.saveAcademicYear.mockResolvedValue(makeYear({ id: 'new' }));
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'New academic year' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.type(within(dialog).getByLabelText(/^name/i), '2028-2029');
    await userEvent.type(within(dialog).getByLabelText(/start date/i), '2028-06-01');
    await userEvent.type(within(dialog).getByLabelText(/end date/i), '2029-03-31');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() =>
      expect(api.saveAcademicYear).toHaveBeenCalledWith(undefined, {
        name: '2028-2029',
        startDate: '2028-06-01',
        endDate: '2029-03-31',
        version: undefined,
      }),
    );
    expect(await screen.findByText('Academic year created')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /new academic year/i })).not.toBeInTheDocument(),
    );
  });

  it('sends the current version when editing and shows a conflict message in the dialog', async () => {
    api.saveAcademicYear.mockRejectedValue(
      new ApiError('This record was changed by someone else. Reload it and try again.', {
        status: 409,
      }),
    );
    api.listAcademicYears.mockResolvedValue(pageOf([{ ...planned, version: 3 }]));
    renderPage();
    await openActions('2027-2028');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Edit' }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('changed by someone else');
    expect(api.saveAcademicYear).toHaveBeenCalledWith(
      'y-planned',
      expect.objectContaining({ version: 3 }),
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('asks for confirmation before activating and then activates', async () => {
    api.activateAcademicYear.mockResolvedValue({ ...planned, status: 'ACTIVE' });
    renderPage();
    await openActions('2027-2028');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Activate' }));

    const dialog = await screen.findByRole('dialog', { name: /activate academic year/i });
    expect(api.activateAcademicYear).not.toHaveBeenCalled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Activate' }));

    await waitFor(() => expect(api.activateAcademicYear).toHaveBeenCalledWith('y-planned'));
    expect(await screen.findByText('Academic year activated')).toBeInTheDocument();
  });

  it('keeps the confirmation open and explains why activation was refused', async () => {
    api.activateAcademicYear.mockRejectedValue(
      new ApiError(
        'Another academic year is already active. Close it before activating this one.',
        {
          status: 409,
        },
      ),
    );
    renderPage();
    await openActions('2027-2028');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Activate' }));
    const dialog = await screen.findByRole('dialog', { name: /activate academic year/i });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Activate' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('already active');
    expect(screen.getByRole('dialog', { name: /activate academic year/i })).toBeInTheDocument();
  });

  it('warns that closing is irreversible and can be cancelled without calling the API', async () => {
    renderPage();
    await openActions('2026-2027');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Close year' }));
    const dialog = await screen.findByRole('dialog', { name: /close academic year/i });

    expect(within(dialog).getByText(/cannot be undone/i)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    expect(api.closeAcademicYear).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('deletes a planned year after confirmation', async () => {
    api.deleteAcademicYear.mockResolvedValue(undefined);
    renderPage();
    await openActions('2027-2028');
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    const dialog = await screen.findByRole('dialog', { name: /delete academic year/i });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(api.deleteAcademicYear).toHaveBeenCalledWith('y-planned'));
  });

  it('filters by status and searches after the user stops typing', async () => {
    renderPage();
    await screen.findByRole('cell', { name: '2027-2028' });

    await userEvent.click(screen.getByRole('combobox', { name: /status/i }));
    await userEvent.click(await screen.findByRole('option', { name: 'Closed' }));
    await userEvent.type(screen.getByRole('searchbox', { name: /search by name/i }), '2025');

    await waitFor(() =>
      expect(api.listAcademicYears).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'CLOSED', search: '2025', page: 0 }),
      ),
    );
  });

  it('shows an empty state with guidance when there are no years', async () => {
    api.listAcademicYears.mockResolvedValue(pageOf([]));
    renderPage();

    expect(await screen.findByText('No academic years found')).toBeInTheDocument();
    expect(screen.getByText(/create the first academic year/i)).toBeInTheDocument();
  });

  it('shows a safe error with retry when loading fails', async () => {
    api.listAcademicYears.mockRejectedValueOnce(
      new ApiError('Something went wrong on our side. Please try again later.', { status: 500 }),
    );
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on our side');
    api.listAcademicYears.mockResolvedValue(pageOf([planned]));
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByRole('cell', { name: '2027-2028' })).toBeInTheDocument();
  });

  it('shows years as cards on a phone', async () => {
    setViewportWidth(375);
    renderPage();

    expect(await screen.findAllByRole('listitem')).toHaveLength(3);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
