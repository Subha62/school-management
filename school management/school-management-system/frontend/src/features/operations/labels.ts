import type { AttendanceStatus, DayName, SessionType } from '../../types/operations';

export const DAYS: DayName[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export const DAY_LABELS: Record<DayName, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
};

export const STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: 'Present',
  ABSENT: 'Absent',
  LATE: 'Late',
  HALF_DAY: 'Half day',
  EXCUSED: 'Excused',
};

export const STATUS_ORDER: AttendanceStatus[] = [
  'PRESENT',
  'ABSENT',
  'LATE',
  'HALF_DAY',
  'EXCUSED',
];

export const TYPE_LABELS: Record<SessionType, string> = {
  DAILY: 'Daily',
  SUBJECT: 'Subject-wise',
};

export const statusTone = (status: AttendanceStatus) =>
  status === 'PRESENT' || status === 'LATE'
    ? 'success'
    : status === 'ABSENT'
      ? 'warning'
      : status === 'HALF_DAY'
        ? 'info'
        : 'default';

export const formatPercentage = (value: number | null): string =>
  value === null ? 'No data' : `${value.toFixed(1)}%`;
