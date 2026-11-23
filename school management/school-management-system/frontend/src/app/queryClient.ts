import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/errors';

const MAX_RETRIES = 2;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Client errors will not succeed on retry; only transient failures are retried.
        retry: (failureCount, error) => {
          const clientError =
            error instanceof ApiError && error.status !== null && error.status < 500;
          return !clientError && failureCount < MAX_RETRIES;
        },
      },
    },
  });
}
