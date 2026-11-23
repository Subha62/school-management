import { createTheme } from '@mui/material/styles';

const MIN_TOUCH_TARGET = 44;

export const theme = createTheme({
  palette: {
    primary: { main: '#1565c0' },
    secondary: { main: '#00695c' },
    background: { default: '#f5f7fa' },
  },
  shape: { borderRadius: 8 },
  components: {
    MuiButton: { styleOverrides: { root: { minHeight: MIN_TOUCH_TARGET } } },
    MuiIconButton: {
      styleOverrides: { root: { minWidth: MIN_TOUCH_TARGET, minHeight: MIN_TOUCH_TARGET } },
    },
    MuiListItemButton: { styleOverrides: { root: { minHeight: MIN_TOUCH_TARGET } } },
  },
});
