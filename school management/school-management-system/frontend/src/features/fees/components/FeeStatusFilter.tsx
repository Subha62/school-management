import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import type { FeeStatus } from '../../../types/fees';
import { FEE_STATUS_LABELS, FEE_STATUS_ORDER } from '../labels';

interface FeeStatusFilterProps {
  /** Undefined means every status. */
  value: FeeStatus | undefined;
  onChange: (value: FeeStatus | undefined) => void;
}

/** Filter chips for the status of a fee. The selected chip is announced through `aria-pressed`. */
export function FeeStatusFilter({ value, onChange }: FeeStatusFilterProps) {
  const options: { id: FeeStatus | undefined; label: string }[] = [
    { id: undefined, label: 'All' },
    ...FEE_STATUS_ORDER.map((status) => ({ id: status, label: FEE_STATUS_LABELS[status] })),
  ];
  return (
    <Stack
      role="group"
      aria-label="Filter by status"
      direction="row"
      spacing={1}
      useFlexGap
      sx={{ flexWrap: 'wrap' }}
    >
      {options.map((option) => {
        const selected = option.id === value;
        return (
          <Chip
            key={option.label}
            label={option.label}
            clickable
            color={selected ? 'primary' : 'default'}
            variant={selected ? 'filled' : 'outlined'}
            aria-pressed={selected}
            onClick={() => onChange(option.id)}
            sx={{ height: 44, borderRadius: 22 }}
          />
        );
      })}
    </Stack>
  );
}
