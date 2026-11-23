import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { ErrorState } from '../components/ErrorState';
import { LoadingState } from '../components/LoadingState';
import type { Role } from '../types/auth';
import { useAuth } from './authContext';
import { hasRole, homePathFor } from './roles';

/**
 * Keeps people out of screens that are not theirs. This is a usability measure only: every API call is
 * authorized again by the backend.
 */
export function RequireRole({
  requiredRole,
  children,
}: {
  requiredRole: Role;
  children: ReactNode;
}) {
  const { user, isLoading, error, retry } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <LoadingState label="Checking your session" />;
  }
  if (error) {
    return <ErrorState title="Unable to verify your session" error={error} onRetry={retry} />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (!hasRole(user, requiredRole)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}

/** Entry point for `/`: sends visitors to the login page and members to their workspace. */
export function HomeRedirect() {
  const { user, isLoading, error, retry } = useAuth();

  if (isLoading) {
    return <LoadingState label="Checking your session" />;
  }
  if (error) {
    return <ErrorState title="Unable to verify your session" error={error} onRetry={retry} />;
  }
  return <Navigate to={user ? homePathFor(user) : '/login'} replace />;
}
