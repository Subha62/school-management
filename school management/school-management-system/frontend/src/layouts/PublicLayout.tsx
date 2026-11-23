import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import { Outlet } from 'react-router-dom';

/** Minimal chrome for pages shown outside the role layouts (home, not found, unauthorized). */
export function PublicLayout() {
  return (
    <Container component="main" maxWidth="md" sx={{ py: { xs: 3, md: 6 } }}>
      <Box>
        <Outlet />
      </Box>
    </Container>
  );
}
