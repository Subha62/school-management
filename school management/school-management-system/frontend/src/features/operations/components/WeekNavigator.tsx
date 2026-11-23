import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { addDays, formatWeek, mondayOf, todayIso } from '../../../utils/dates';

interface WeekNavigatorProps {
  /** Monday of the displayed week as `YYYY-MM-DD`. */
  weekStart: string;
  onChange: (weekStart: string) => void;
}

export function WeekNavigator({ weekStart, onChange }: WeekNavigatorProps) {
  const thisWeek = mondayOf(todayIso());
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }} useFlexGap>
      <IconButton aria-label="Previous week" onClick={() => onChange(addDays(weekStart, -7))}>
        <ChevronLeftIcon />
      </IconButton>
      <Typography component="span" sx={{ minWidth: 190, textAlign: 'center', fontWeight: 600 }}>
        {formatWeek(weekStart)}
      </Typography>
      <IconButton aria-label="Next week" onClick={() => onChange(addDays(weekStart, 7))}>
        <ChevronRightIcon />
      </IconButton>
      <Button size="small" disabled={weekStart === thisWeek} onClick={() => onChange(thisWeek)}>
        This week
      </Button>
    </Stack>
  );
}
