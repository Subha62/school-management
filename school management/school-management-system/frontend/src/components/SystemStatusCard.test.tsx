import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiError } from '../api/errors';
import { renderWithProviders } from '../test/renderWithProviders';
import { SystemStatusCard } from './SystemStatusCard';

const useSystemInfo = vi.hoisted(() => vi.fn());
vi.mock('../hooks/useSystemInfo', () => ({ useSystemInfo }));

describe('SystemStatusCard', () => {
  it('shows a loading indicator while the status is pending', () => {
    useSystemInfo.mockReturnValue({
      data: undefined,
      error: null,
      isPending: true,
      refetch: vi.fn(),
    });

    renderWithProviders(<SystemStatusCard />);

    expect(
      screen.getByRole('progressbar', { name: /checking backend status/i }),
    ).toBeInTheDocument();
  });

  it('shows the backend as online with the active profile', () => {
    useSystemInfo.mockReturnValue({
      data: { name: 'school-management-system', status: 'UP', profile: 'dev' },
      error: null,
      isPending: false,
      refetch: vi.fn(),
    });

    renderWithProviders(<SystemStatusCard />);

    expect(screen.getByText('Backend online')).toBeInTheDocument();
    expect(screen.getByText('Profile: dev')).toBeInTheDocument();
  });

  it('shows a safe error message with trace reference and allows retrying', async () => {
    const refetch = vi.fn();
    useSystemInfo.mockReturnValue({
      data: undefined,
      error: new ApiError('Something went wrong on our side. Please try again later.', {
        status: 500,
        traceId: 'abc-123',
      }),
      isPending: false,
      refetch,
    });

    renderWithProviders(<SystemStatusCard />);

    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong on our side');
    expect(screen.getByRole('alert')).toHaveTextContent('abc-123');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });
});
