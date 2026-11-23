import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useSystemInfo } from '../hooks/useSystemInfo';
import { ErrorState } from './ErrorState';
import { LoadingState } from './LoadingState';

export function SystemStatusCard() {
  const { data, error, isPending, refetch } = useSystemInfo();

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" component="h2" gutterBottom>
          Backend status
        </Typography>
        {isPending ? <LoadingState label="Checking backend status" /> : null}
        {error ? (
          <ErrorState title="Backend unavailable" error={error} onRetry={() => void refetch()} />
        ) : null}
        {data ? (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Chip color="success" label="Backend online" />
            <Chip variant="outlined" label={data.name} />
            {data.profile ? <Chip variant="outlined" label={`Profile: ${data.profile}`} /> : null}
          </Stack>
        ) : null}
      </CardContent>
    </Card>
  );
}
