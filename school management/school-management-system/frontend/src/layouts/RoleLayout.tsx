import MenuIcon from '@mui/icons-material/Menu';
import SchoolIcon from '@mui/icons-material/School';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { alpha, useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { Fragment, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { AccountMenu } from '../components/AccountMenu';
import { useUnreadCount } from '../features/announcements/hooks';
import { navigationByRole, type NavItem, type Role } from './navigation';

const DRAWER_WIDTH = 272;

const paperSx = {
  boxSizing: 'border-box',
  bgcolor: 'background.paper',
  borderRight: '1px solid',
  borderColor: 'divider',
  scrollbarWidth: 'thin',
  '&::-webkit-scrollbar': { width: 6 },
  '&::-webkit-scrollbar-thumb': { backgroundColor: 'rgba(0,0,0,0.18)', borderRadius: 3 },
} as const;

function joinPath(basePath: string, path: string): string {
  return path ? `${basePath}/${path}` : basePath;
}

interface RoleLayoutProps {
  userRole: Role;
}

export function RoleLayout({ userRole }: RoleLayoutProps) {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const navigation = navigationByRole[userRole];
  const unread = useUnreadCount().data?.count ?? 0;

  const closeMobileDrawer = () => setMobileOpen(false);

  const isActive = (item: NavItem): boolean => {
    const target = joinPath(navigation.basePath, item.path);
    return item.path === ''
      ? location.pathname === target
      : location.pathname === target || location.pathname.startsWith(`${target}/`);
  };

  const drawerContent = (
    <Box component="nav" aria-label={`${navigation.title} navigation`} sx={{ pb: 2 }}>
      <Toolbar />
      {navigation.groups.map((group, groupIndex) => (
        <Fragment key={group.title ?? groupIndex}>
          {groupIndex > 0 && <Divider sx={{ mx: 2, my: 0.5 }} />}
          <List
            dense={false}
            sx={{ px: 1.5, py: 1 }}
            subheader={
              group.title ? (
                <ListSubheader
                  component="div"
                  disableSticky
                  sx={{
                    bgcolor: 'transparent',
                    lineHeight: 1.5,
                    px: 1.5,
                    pt: 1,
                    pb: 0.75,
                    fontSize: '0.6875rem',
                    fontWeight: 700,
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase',
                  }}
                >
                  {group.title}
                </ListSubheader>
              ) : undefined
            }
          >
            {group.items.map((item) => {
              const Icon = item.icon;
              const to = joinPath(navigation.basePath, item.path);
              return (
                <ListItemButton
                  key={item.label}
                  {...(item.available
                    ? { component: NavLink, to, onClick: closeMobileDrawer, end: item.path === '' }
                    : { disabled: true })}
                  selected={item.available && isActive(item)}
                  sx={{
                    position: 'relative',
                    borderRadius: 2,
                    mb: 0.25,
                    px: 1.5,
                    color: 'text.secondary',
                    transition: 'background-color 120ms, color 120ms',
                    '& .MuiListItemIcon-root': { color: 'inherit', minWidth: 40 },
                    '&:hover': { bgcolor: 'action.hover', color: 'text.primary' },
                    '&.Mui-selected': {
                      bgcolor: (t) => alpha(t.palette.primary.main, 0.1),
                      color: 'primary.main',
                      fontWeight: 600,
                      '&:hover': { bgcolor: (t) => alpha(t.palette.primary.main, 0.16) },
                      '&::before': {
                        content: '""',
                        position: 'absolute',
                        left: 0,
                        top: 10,
                        bottom: 10,
                        width: 3,
                        borderRadius: 2,
                        bgcolor: 'primary.main',
                      },
                    },
                    '&.Mui-selected .MuiListItemText-primary': { fontWeight: 600 },
                    '&.Mui-focusVisible, &:focus-visible': {
                      outline: (t) => `2px solid ${t.palette.primary.main}`,
                      outlineOffset: -2,
                    },
                  }}
                >
                  <ListItemIcon>
                    <Icon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText
                    primary={item.label}
                    secondary={item.available ? undefined : 'Coming soon'}
                    slotProps={{
                      primary: { noWrap: true, variant: 'body2', sx: { fontWeight: 'inherit' } },
                      secondary: { variant: 'caption' },
                    }}
                  />
                  {item.badge === 'unread-announcements' && unread > 0 ? (
                    <Chip
                      size="small"
                      color="error"
                      label={
                        <>
                          {unread > 99 ? '99+' : unread}
                          <Box
                            component="span"
                            sx={{
                              position: 'absolute',
                              width: '1px',
                              height: '1px',
                              overflow: 'hidden',
                              clipPath: 'inset(50%)',
                            }}
                          >
                            {' unread'}
                          </Box>
                        </>
                      }
                    />
                  ) : null}
                </ListItemButton>
              );
            })}
          </List>
        </Fragment>
      ))}
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <Box
        component="a"
        href="#main-content"
        sx={{
          position: 'absolute',
          left: 8,
          top: -64,
          zIndex: (t) => t.zIndex.tooltip + 1,
          bgcolor: 'background.paper',
          px: 2,
          py: 1,
          borderRadius: 1,
          '&:focus': { top: 8 },
        }}
      >
        Skip to main content
      </Box>

      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          {!isDesktop && (
            <IconButton
              color="inherit"
              edge="start"
              aria-label="Open navigation menu"
              onClick={() => setMobileOpen(true)}
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <SchoolIcon sx={{ mr: 1 }} aria-hidden />
          <Typography variant="h6" component="p" noWrap sx={{ flexGrow: 1, minWidth: 0 }}>
            School Management
          </Typography>
          <Typography variant="body2" noWrap sx={{ ml: 1, display: { xs: 'none', sm: 'block' } }}>
            {navigation.title}
          </Typography>
          <AccountMenu basePath={navigation.basePath} />
        </Toolbar>
      </AppBar>

      {isDesktop ? (
        <Drawer
          variant="permanent"
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            '& .MuiDrawer-paper': { ...paperSx, width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>
      ) : (
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={closeMobileDrawer}
          sx={{
            '& .MuiDrawer-paper': { ...paperSx, width: DRAWER_WIDTH, maxWidth: '85vw' },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      <Box
        component="main"
        id="main-content"
        tabIndex={-1}
        sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, sm: 3 }, outline: 'none' }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
