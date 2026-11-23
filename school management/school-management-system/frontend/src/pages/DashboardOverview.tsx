import Grid from '@mui/material/Grid';
import type { ReactNode } from 'react';
import type { ApiError } from '../api/errors';
import { useAuth } from '../auth/authContext';
import { ErrorState } from '../components/ErrorState';
import { LoadingState } from '../components/LoadingState';
import { PageHeader } from '../components/PageHeader';
import { SystemStatusCard } from '../components/SystemStatusCard';

interface DashboardOverviewProps {
  title: string;
  subtitle: string;
  isPending: boolean;
  error: ApiError | null;
  onRetry: () => void;
  /** The role's widgets (`Widget` grid items); shown once the dashboard data has loaded. */
  children?: ReactNode;
}

/** Shared dashboard frame: heading, the widgets, and the backend status card, which stays visible if loading fails. */
export function DashboardOverview({
  title,
  subtitle,
  isPending,
  error,
  onRetry,
  children,
}: DashboardOverviewProps) {
  const { user } = useAuth();

  return (
    <>
      <PageHeader
        title={title}
        subtitle={user ? `Welcome, ${user.firstName}. ${subtitle}` : subtitle}
      />
      {isPending ? <LoadingState label="Loading dashboard" /> : null}
      {error ? (
        <Grid container spacing={2} sx={{ mb: 2 }}>
          <Grid size={12}>
            <ErrorState title="Unable to load the dashboard" error={error} onRetry={onRetry} />
          </Grid>
        </Grid>
      ) : null}
      <Grid container spacing={2}>
        {error || isPending ? null : children}
        <Grid size={{ xs: 12, sm: 6, lg: 4 }} sx={{ minWidth: 0 }}>
          <SystemStatusCard />
        </Grid>
      </Grid>
    </>
  );
}
