const pad = (value: number) => String(value).padStart(2, '0');

/** Calendar dates are handled as local `YYYY-MM-DD` strings, so they never shift with the time zone. */
export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function fromIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}

export const todayIso = (): string => toIsoDate(new Date());

export function addDays(iso: string, days: number): string {
  const date = fromIsoDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** The Monday of the week that contains the date. */
export function mondayOf(iso: string): string {
  const date = fromIsoDate(iso);
  const sinceMonday = (date.getDay() + 6) % 7;
  return addDays(iso, -sinceMonday);
}

export const currentMonth = (): string => todayIso().slice(0, 7);

export function shiftMonth(month: string, delta: number): string {
  const [year, value] = month.split('-').map(Number);
  const date = new Date(year ?? 1970, (value ?? 1) - 1 + delta, 1);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
}

const rangeFormat = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });
const rangeFormatWithYear = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});

export function formatWeek(weekStart: string): string {
  const end = fromIsoDate(addDays(weekStart, 6));
  return `${rangeFormat.format(fromIsoDate(weekStart))} - ${rangeFormatWithYear.format(end)}`;
}

/** `09:00:00` or `09:00` to `09:00`. */
export const shortTime = (time: string): string => time.slice(0, 5);
