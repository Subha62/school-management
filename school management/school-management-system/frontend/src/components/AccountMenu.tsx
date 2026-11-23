import AccountCircle from '@mui/icons-material/AccountCircle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import { useId, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/authContext';
import { fullName } from '../auth/roles';

const SIGN_OUT_FAILED = 'Could not sign out. Check your connection and try again.';

/** Account actions for the signed-in user: profile, password change and sign out. */
export function AccountMenu({ basePath }: { basePath: string }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [signOutFailed, setSignOutFailed] = useState(false);
  const menuId = useId();

  if (!user) {
    return null;
  }

  const close = () => setAnchor(null);
  const signOut = async () => {
    close();
    try {
      await logout();
      navigate('/login', { replace: true });
    } catch {
      // The session is still active on the server, so the user is told rather than shown as signed out.
      setSignOutFailed(true);
    }
  };

  return (
    <>
      <IconButton
        color="inherit"
        aria-label="Account menu"
        aria-haspopup="menu"
        aria-controls={anchor ? menuId : undefined}
        aria-expanded={anchor ? true : undefined}
        onClick={(event) => setAnchor(event.currentTarget)}
      >
        <AccountCircle />
      </IconButton>
      <Menu id={menuId} anchorEl={anchor} open={Boolean(anchor)} onClose={close}>
        <MenuItem disabled sx={{ opacity: '1 !important' }}>
          <ListItemText
            primary={fullName(user)}
            secondary={user.email}
            slotProps={{ secondary: { sx: { overflowWrap: 'anywhere' } } }}
          />
        </MenuItem>
        <Divider />
        <MenuItem component={RouterLink} to={`${basePath}/profile`} onClick={close}>
          Profile
        </MenuItem>
        <MenuItem component={RouterLink} to={`${basePath}/change-password`} onClick={close}>
          Change password
        </MenuItem>
        <MenuItem onClick={() => void signOut()}>Sign out</MenuItem>
      </Menu>
      <Snackbar
        open={signOutFailed}
        autoHideDuration={6000}
        onClose={() => setSignOutFailed(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setSignOutFailed(false)}>
          {SIGN_OUT_FAILED}
        </Alert>
      </Snackbar>
    </>
  );
}
