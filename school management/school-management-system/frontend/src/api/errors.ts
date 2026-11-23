import { isAxiosError } from 'axios';
import type { FieldError, ProblemDetail } from '../types/api';

const GENERIC_SERVER_MESSAGE = 'Something went wrong on our side. Please try again later.';
const NETWORK_MESSAGE = 'Unable to reach the server. Check your connection and try again.';
const UNAUTHORIZED_MESSAGE = 'Your session has expired. Please sign in again.';
const FORBIDDEN_MESSAGE = 'You do not have permission to perform this action.';

/**
 * Normalised error used throughout the UI. `message` is always safe to display; raw backend payloads
 * are never exposed to users.
 */
export class ApiError extends Error {
  readonly status: number | null;
  readonly fieldErrors: Record<string, string>;
  readonly traceId: string | null;

  constructor(
    message: string,
    options: {
      status?: number | null;
      fieldErrors?: Record<string, string>;
      traceId?: string | null;
    } = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = options.status ?? null;
    this.fieldErrors = options.fieldErrors ?? {};
    this.traceId = options.traceId ?? null;
  }
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.status === 'number' && typeof candidate.title === 'string';
}

function toFieldErrorMap(errors: FieldError[] | undefined): Record<string, string> {
  const map: Record<string, string> = {};
  for (const error of errors ?? []) {
    if (!(error.field in map)) {
      map[error.field] = error.message;
    }
  }
  return map;
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (!isAxiosError(error)) {
    return new ApiError(GENERIC_SERVER_MESSAGE);
  }
  if (!error.response) {
    return new ApiError(NETWORK_MESSAGE);
  }

  const { status, data } = error.response;
  const problem = isProblemDetail(data) ? data : null;
  const traceId = problem?.traceId ?? null;

  if (status >= 500) {
    return new ApiError(GENERIC_SERVER_MESSAGE, { status, traceId });
  }
  if (status === 401) {
    return new ApiError(UNAUTHORIZED_MESSAGE, { status, traceId });
  }
  if (status === 403) {
    return new ApiError(FORBIDDEN_MESSAGE, { status, traceId });
  }
  return new ApiError(problem?.detail ?? problem?.title ?? 'The request could not be completed.', {
    status,
    traceId,
    fieldErrors: toFieldErrorMap(problem?.errors),
  });
}
