import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, type ReactNode } from 'react';
import * as authApi from '../api/auth';
import { setSessionExpiredHandler } from '../api/client';
import { ApiError } from '../api/errors';
import type { CurrentUser, LoginPayload } from '../types/auth';
import { AuthContext, currentUserQueryKey, type AuthContextValue } from './authContext';

/** Anonymous visitors are a normal state, not an error: a 401 resolves to null. */
async function loadCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await authApi.fetchCurrentUser();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const query = useQuery<CurrentUser | null, ApiError>({
    queryKey: currentUserQueryKey,
    queryFn: loadCurrentUser,
    staleTime: Infinity,
    retry: false,
  });

  const clearSession = useCallback(() => {
    queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== currentUserQueryKey[0] });
    queryClient.setQueryData(currentUserQueryKey, null);
  }, [queryClient]);

  useEffect(() => {
    setSessionExpiredHandler(clearSession);
    return () => setSessionExpiredHandler(null);
  }, [clearSession]);

  const login = useCallback(
    async (payload: LoginPayload) => {
      const user = await authApi.login(payload);
      queryClient.setQueryData(currentUserQueryKey, user);
      return user;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    // If the request fails the cookies are still valid, so the user must not be shown as signed out.
    await authApi.logout();
    clearSession();
  }, [clearSession]);

  const refetch = query.refetch;
  const value = useMemo<AuthContextValue>(
    () => ({
      user: query.data ?? null,
      isLoading: query.isPending,
      error: query.error,
      retry: () => void refetch(),
      login,
      logout,
    }),
    [query.data, query.isPending, query.error, refetch, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
