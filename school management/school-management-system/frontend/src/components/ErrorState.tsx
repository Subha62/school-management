import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Button from '@mui/material/Button';
import type { ApiError } from '../api/errors';

interface ErrorStateProps {
  title?: string;
  error: ApiError;
  onRetry?: () => void;
}

export function ErrorState({ title = 'Unable to load data', error, onRetry }: ErrorStateProps) {
  return (
    <Alert
      severity="error"
      action={
        onRetry ? (
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        ) : undefined
      }
    >
      <AlertTitle>{title}</AlertTitle>
      {error.message}
      {error.traceId ? ` (Reference: ${error.traceId})` : null}
    </Alert>
  );
}
