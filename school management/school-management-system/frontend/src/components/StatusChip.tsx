import Chip from '@mui/material/Chip';

type Tone = 'success' | 'default' | 'warning' | 'info';

export function StatusChip({ label, tone = 'default' }: { label: string; tone?: Tone }) {
  return (
    <Chip
      label={label}
      size="small"
      color={tone}
      variant={tone === 'default' ? 'outlined' : 'filled'}
    />
  );
}

export function ActiveChip({ active }: { active: boolean }) {
  return (
    <StatusChip label={active ? 'Active' : 'Inactive'} tone={active ? 'success' : 'default'} />
  );
}
