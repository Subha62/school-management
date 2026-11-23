import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string;
  hint?: string;
  action?: ReactNode;
}) {
  return (
    <Box sx={{ textAlign: 'center', py: 6, px: 2 }}>
      <Typography variant="h6" component="p" gutterBottom>
        {title}
      </Typography>
      {hint ? (
        <Typography color="text.secondary" sx={{ mb: action ? 2 : 0 }}>
          {hint}
        </Typography>
      ) : null}
      {action}
    </Box>
  );
}
