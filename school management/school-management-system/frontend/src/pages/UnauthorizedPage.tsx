import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

export function UnauthorizedPage() {
  return (
    <>
      <PageHeader title="Access denied" />
      <Typography sx={{ mb: 3 }}>You do not have permission to view this page.</Typography>
      <Button variant="contained" component={RouterLink} to="/">
        Back to home
      </Button>
    </>
  );
}
