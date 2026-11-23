import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import type { ReactNode } from 'react';

interface ResponsiveDialogProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  /** Id of the form inside `children`; the submit button is attached to it. */
  formId: string;
  submitLabel: string;
  submitting?: boolean;
}

/** Full screen on phones, a normal modal from the `sm` breakpoint up. */
export function ResponsiveDialog({
  title,
  onClose,
  children,
  formId,
  submitLabel,
  submitting = false,
}: ResponsiveDialogProps) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));

  return (
    <Dialog
      open
      onClose={submitting ? undefined : onClose}
      fullScreen={fullScreen}
      fullWidth
      maxWidth="sm"
      aria-labelledby={`${formId}-title`}
    >
      <DialogTitle id={`${formId}-title`}>{title}</DialogTitle>
      <DialogContent dividers>{children}</DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" form={formId} variant="contained" loading={submitting}>
          {submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
