import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/errors';
import { renderWithProviders } from '../test/renderWithProviders';
import { setViewportWidth } from '../test/viewport';
import type { PageResponse } from '../types/api';
import { DataList, type Column, type RowAction } from './DataList';

interface Row {
  id: string;
  name: string;
  code: string;
  note: string;
}

const rows: Row[] = [
  { id: '1', name: 'Mathematics', code: 'MATH', note: 'Core subject' },
  { id: '2', name: 'English', code: 'ENG', note: 'Language' },
];

const page = (content: Row[], extra: Partial<PageResponse<Row>> = {}): PageResponse<Row> => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: 1,
  first: true,
  last: true,
  ...extra,
});

const columns: Column<Row>[] = [
  { id: 'name', header: 'Name', render: (r) => r.name },
  { id: 'code', header: 'Code', render: (r) => r.code },
  { id: 'note', header: 'Note', hideBelow: 'lg', render: (r) => r.note },
];

function renderList(
  data: PageResponse<Row> | undefined,
  overrides: Partial<Parameters<typeof DataList<Row>>[0]> = {},
) {
  const props = {
    data,
    columns,
    getRowId: (r: Row) => r.id,
    getRowLabel: (r: Row) => r.name,
    isLoading: false,
    error: null,
    onRetry: vi.fn(),
    emptyTitle: 'Nothing here',
    onPageChange: vi.fn(),
    onPageSizeChange: vi.fn(),
    ...overrides,
  };
  renderWithProviders(<DataList<Row> {...props} />);
  return props;
}

describe('DataList', () => {
  it('renders a table with column headers on desktop', () => {
    setViewportWidth(1440);
    renderList(page(rows));

    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Code' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Mathematics' })).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('renders cards instead of a table on mobile and keeps every field readable', () => {
    setViewportWidth(375);
    renderList(page(rows));

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    const cards = screen.getAllByRole('listitem');
    expect(cards).toHaveLength(2);
    expect(within(cards[0]!).getByText('Mathematics')).toBeInTheDocument();
    expect(within(cards[0]!).getByText('MATH')).toBeInTheDocument();
    // Columns hidden in the desktop table are still shown on the card.
    expect(within(cards[0]!).getByText('Core subject')).toBeInTheDocument();
  });

  it('opens a per-row action menu and calls the chosen action with that row', async () => {
    setViewportWidth(1440);
    const onEdit = vi.fn();
    const onDelete = vi.fn();
    const actions: RowAction<Row>[] = [
      { label: 'Edit', onSelect: onEdit },
      { label: 'Delete', onSelect: onDelete, destructive: true },
    ];
    renderList(page(rows), { actions });

    await userEvent.click(screen.getByRole('button', { name: 'Actions for English' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));

    expect(onDelete).toHaveBeenCalledWith(rows[1]);
    expect(onEdit).not.toHaveBeenCalled();
  });

  it('hides actions that do not apply to a row, and the menu when none apply', () => {
    setViewportWidth(375);
    const actions: RowAction<Row>[] = [
      { label: 'Edit', onSelect: vi.fn(), hidden: (r) => r.id === '1' },
    ];
    renderList(page(rows), { actions });

    expect(
      screen.queryByRole('button', { name: 'Actions for Mathematics' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Actions for English' })).toBeInTheDocument();
  });

  it('shows an empty state with its hint', () => {
    renderList(page([]), { emptyTitle: 'No subjects found', emptyHint: 'Add one to begin.' });

    expect(screen.getByText('No subjects found')).toBeInTheDocument();
    expect(screen.getByText('Add one to begin.')).toBeInTheDocument();
  });

  it('shows a loading indicator before the first page arrives', () => {
    renderList(undefined, { isLoading: true });

    expect(screen.getByRole('progressbar', { name: /loading list/i })).toBeInTheDocument();
  });

  it('shows a safe error with a retry when nothing could be loaded', async () => {
    const props = renderList(undefined, {
      error: new ApiError('Unable to reach the server.', { traceId: 'abc' }),
    });

    expect(screen.getByRole('alert')).toHaveTextContent('Unable to reach the server.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(props.onRetry).toHaveBeenCalled();
  });

  it('keeps showing the previous rows when a refresh fails', () => {
    setViewportWidth(1440);
    renderList(page(rows), { error: new ApiError('Something went wrong on our side.') });

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
    expect(screen.getByRole('cell', { name: 'Mathematics' })).toBeInTheDocument();
  });

  it('reports page and page-size changes', async () => {
    setViewportWidth(1440);
    const props = renderList(page(rows, { totalElements: 45, totalPages: 3, last: false }));

    await userEvent.click(screen.getByRole('button', { name: /go to next page/i }));
    expect(props.onPageChange).toHaveBeenCalledWith(1);

    await userEvent.click(screen.getByRole('combobox', { name: /rows/i }));
    await userEvent.click(await screen.findByRole('option', { name: '50' }));
    expect(props.onPageSizeChange).toHaveBeenCalledWith(50);
  });
});
