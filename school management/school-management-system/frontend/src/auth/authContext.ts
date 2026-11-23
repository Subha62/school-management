import { createContext, useContext } from 'react';
import type { ApiError } from '../api/errors';
import type { CurrentUser, LoginPayload } from '../types/auth';

export interface AuthContextValue {
  /** The signed-in user, or null when anonymous. */
  user: CurrentUser | null;
  /** True until the first identity check has finished. */
  isLoading: boolean;
  /** Set when the identity check failed for a reason other than "not signed in" (for example offline). */
  error: ApiError | null;
  retry: () => void;
  login: (payload: LoginPayload) => Promise<CurrentUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export const currentUserQueryKey = ['auth', 'me'] as const;

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
