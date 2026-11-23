import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" />
      <Typography sx={{ mb: 3 }}>The page you are looking for does not exist.</Typography>
      <Button variant="contained" component={RouterLink} to="/">
        Back to home
      </Button>
    </>
  );
}
