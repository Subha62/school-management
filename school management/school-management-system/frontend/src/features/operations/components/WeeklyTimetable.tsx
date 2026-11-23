import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useMemo, useState } from 'react';
import { EmptyState } from '../../../components/EmptyState';
import { StatusChip } from '../../../components/StatusChip';
import type { DayName, TimetableEntry } from '../../../types/operations';
import { formatDate } from '../../../utils/format';
import { shortTime } from '../../../utils/dates';
import { DAYS, DAY_LABELS } from '../labels';

interface WeeklyTimetableProps {
  entries: TimetableEntry[];
  /** The second line of each slot, for example the teacher or the class. */
  detail: (entry: TimetableEntry) => string;
  emptyTitle: string;
  emptyHint?: string;
  /** Slot actions, used by the administrator's editor. */
  onEdit?: (entry: TimetableEntry) => void;
  onDelete?: (entry: TimetableEntry) => void;
  /** Day shown first on small screens, as a weekday name. */
  initialDay?: DayName;
}

const WEEKDAYS = DAYS.slice(0, 6);

function dayOfToday(): DayName {
  return DAYS[(new Date().getDay() + 6) % 7] ?? 'MONDAY';
}

function Slot({
  entry,
  detail,
  onEdit,
  onDelete,
}: Pick<WeeklyTimetableProps, 'onEdit' | 'onDelete'> & {
  entry: TimetableEntry;
  detail: (entry: TimetableEntry) => string;
}) {
  const label = `${entry.subjectName} ${DAY_LABELS[entry.dayOfWeek]} ${shortTime(entry.startTime)}`;
  return (
    <Card
      variant="outlined"
      component="li"
      sx={{ listStyle: 'none', opacity: entry.active ? 1 : 0.6 }}
    >
      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Typography variant="caption" color="text.secondary">
          {shortTime(entry.startTime)} - {shortTime(entry.endTime)} · Period {entry.periodNumber}
        </Typography>
        <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
          {entry.subjectName}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {detail(entry)}
          {entry.room ? ` · ${entry.room}` : ''}
        </Typography>
        {!entry.active ? <StatusChip label="Inactive" /> : null}
        {onEdit || onDelete ? (
          <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
            {onEdit ? (
              <Button size="small" aria-label={`Edit ${label}`} onClick={() => onEdit(entry)}>
                Edit
              </Button>
            ) : null}
            {onDelete ? (
              <Button
                size="small"
                color="error"
                aria-label={`Delete ${label}`}
                onClick={() => onDelete(entry)}
              >
                Delete
              </Button>
            ) : null}
          </Stack>
        ) : null}
      </CardContent>
    </Card>
  );
}

/**
 * The weekly timetable: a column per day from the `md` breakpoint, and a day selector with an agenda list below
 * it. Saturday is always offered; Sunday only when something is scheduled.
 */
export function WeeklyTimetable({
  entries,
  detail,
  emptyTitle,
  emptyHint,
  onEdit,
  onDelete,
  initialDay,
}: WeeklyTimetableProps) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up('md'));
  const days = useMemo(
    () => (entries.some((entry) => entry.dayOfWeek === 'SUNDAY') ? DAYS : WEEKDAYS),
    [entries],
  );
  const byDay = useMemo(() => {
    const groups = new Map<DayName, TimetableEntry[]>();
    for (const entry of entries) {
      const list = groups.get(entry.dayOfWeek) ?? [];
      list.push(entry);
      groups.set(entry.dayOfWeek, list);
    }
    for (const list of groups.values()) {
      list.sort(
        (a, b) => a.startTime.localeCompare(b.startTime) || a.periodNumber - b.periodNumber,
      );
    }
    return groups;
  }, [entries]);

  const [chosen, setChosen] = useState<DayName | null>(null);
  const preferred = initialDay ?? dayOfToday();
  const selected: DayName = chosen ?? (days.includes(preferred) ? preferred : 'MONDAY');

  if (entries.length === 0) {
    return <EmptyState title={emptyTitle} hint={emptyHint} />;
  }

  if (wide) {
    return (
      <Box
        sx={{
          display: 'grid',
          gap: 1.5,
          gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))`,
          alignItems: 'start',
        }}
      >
        {days.map((day) => {
          const slots = byDay.get(day) ?? [];
          const date = slots.find((slot) => slot.date)?.date;
          return (
            <Box key={day} component="section" aria-label={DAY_LABELS[day]}>
              <Typography variant="subtitle2" component="h3" sx={{ mb: 1 }}>
                {DAY_LABELS[day]}
                {date ? (
                  <Typography component="span" variant="caption" color="text.secondary">
                    {' '}
                    {formatDate(date)}
                  </Typography>
                ) : null}
              </Typography>
              <Stack component="ul" spacing={1} sx={{ m: 0, p: 0 }}>
                {slots.map((slot) => (
                  <Slot
                    key={slot.id}
                    entry={slot}
                    detail={detail}
                    onEdit={onEdit}
                    onDelete={onDelete}
                  />
                ))}
                {slots.length === 0 ? (
                  <Typography
                    variant="body2"
                    color="text.disabled"
                    component="li"
                    sx={{ listStyle: 'none' }}
                  >
                    Nothing scheduled
                  </Typography>
                ) : null}
              </Stack>
            </Box>
          );
        })}
      </Box>
    );
  }

  const slots = byDay.get(selected) ?? [];
  return (
    <Box>
      <Tabs
        value={selected}
        onChange={(_event, value: DayName) => setChosen(value)}
        variant="scrollable"
        scrollButtons="auto"
        aria-label="Day of the week"
        sx={{ mb: 2 }}
      >
        {days.map((day) => (
          <Tab
            key={day}
            value={day}
            label={`${DAY_LABELS[day].slice(0, 3)} (${byDay.get(day)?.length ?? 0})`}
            aria-label={DAY_LABELS[day]}
          />
        ))}
      </Tabs>
      {slots.length === 0 ? (
        <Typography color="text.secondary">Nothing scheduled on {DAY_LABELS[selected]}.</Typography>
      ) : (
        <Stack
          component="ul"
          spacing={1}
          aria-label={`${DAY_LABELS[selected]} schedule`}
          sx={{ m: 0, p: 0 }}
        >
          {slots.map((slot) => (
            <Slot key={slot.id} entry={slot} detail={detail} onEdit={onEdit} onDelete={onDelete} />
          ))}
        </Stack>
      )}
    </Box>
  );
}
