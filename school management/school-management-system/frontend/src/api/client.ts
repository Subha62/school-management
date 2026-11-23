import axios, { isAxiosError, type InternalAxiosRequestConfig } from 'axios';
import { ApiError, toApiError } from './errors';

/**
 * Single Axios instance for the whole application. Authentication uses HttpOnly cookies, so requests
 * always send credentials and echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 15_000,
  headers: { Accept: 'application/json' },
});

/** A 401 from these endpoints is an answer, not an expired session, so it must never trigger a refresh. */
const NO_REFRESH_PATHS = [
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/forgot-password',
  '/auth/reset-password',
];

type RetriableRequest = InternalAxiosRequestConfig & { _refreshAttempted?: boolean };

let sessionExpiredHandler: (() => void) | null = null;
let refreshInFlight: Promise<void> | null = null;

/** Called when a session cannot be renewed, so the app can drop its user and let route guards redirect. */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler;
}

function canRefresh(url: string | undefined): boolean {
  return !NO_REFRESH_PATHS.some((path) => url?.startsWith(path));
}

/** All requests that hit an expired session at the same time share one refresh call. */
function refreshSession(): Promise<void> {
  refreshInFlight ??= apiClient
    .post('/auth/refresh')
    .then(() => undefined)
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    const request = isAxiosError(error)
      ? (error.config as RetriableRequest | undefined)
      : undefined;

    if (
      isAxiosError(error) &&
      error.response?.status === 401 &&
      request &&
      !request._refreshAttempted &&
      canRefresh(request.url)
    ) {
      // One controlled attempt per request: the retry is flagged so it can never start another refresh.
      request._refreshAttempted = true;
      try {
        await refreshSession();
      } catch {
        sessionExpiredHandler?.();
        throw toApiError(error);
      }
      try {
        return await apiClient(request);
      } catch (retryError) {
        const apiError = toApiError(retryError);
        if (apiError.status === 401) {
          sessionExpiredHandler?.();
        }
        throw apiError;
      }
    }
    throw error instanceof ApiError ? error : toApiError(error);
  },
);
