import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import SchoolIcon from '@mui/icons-material/School';
import { Outlet } from 'react-router-dom';

/** Centered card for the sign-in and password-recovery pages. */
export function AuthLayout() {
  return (
    <Container
      component="main"
      maxWidth="xs"
      sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', py: 3 }}
    >
      <Paper variant="outlined" sx={{ width: '100%', p: { xs: 2.5, sm: 4 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
          <SchoolIcon color="primary" aria-hidden />
          <Typography variant="h6" component="p">
            School Management
          </Typography>
        </Box>
        <Outlet />
      </Paper>
    </Container>
  );
}
