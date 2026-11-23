import { addDays, formatWeek, mondayOf, shiftMonth, shortTime, toIsoDate } from './dates';
import { correctionSchema, timetableSchema } from '../schemas/operations';

describe('date helpers', () => {
  it('finds the Monday of any day in the week', () => {
    expect(mondayOf('2026-11-16')).toBe('2026-11-16');
    expect(mondayOf('2026-11-18')).toBe('2026-11-16');
    expect(mondayOf('2026-11-22')).toBe('2026-11-16');
    expect(mondayOf('2026-11-23')).toBe('2026-11-23');
  });

  it('adds days across month and year ends without a time-zone shift', () => {
    expect(addDays('2026-12-30', 3)).toBe('2027-01-02');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });

  it('moves between months and formats a week', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
    expect(formatWeek('2026-11-16')).toMatch(/16/);
    expect(shortTime('09:05:00')).toBe('09:05');
  });
});

const validSlot = {
  subjectId: 'subject-1',
  teacher: null,
  room: '',
  dayOfWeek: 'MONDAY' as const,
  periodNumber: '1',
  startTime: '09:00',
  endTime: '09:45',
  effectiveFrom: '',
  effectiveTo: '',
  active: true,
};

describe('timetableSchema', () => {
  it('accepts a slot with blank optional fields', () => {
    expect(timetableSchema.safeParse(validSlot).success).toBe(true);
  });

  it.each([
    ['endTime', '09:00'],
    ['endTime', '08:00'],
    ['periodNumber', '0'],
    ['periodNumber', '21'],
    ['periodNumber', '1.5'],
    ['subjectId', ''],
  ])('rejects %s = %s', (field, value) => {
    expect(timetableSchema.safeParse({ ...validSlot, [field]: value }).success).toBe(false);
  });

  it('rejects an effective range that ends before it starts', () => {
    const result = timetableSchema.safeParse({
      ...validSlot,
      effectiveFrom: '2026-10-10',
      effectiveTo: '2026-10-01',
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['effectiveTo']);
  });
});

describe('correctionSchema', () => {
  it('needs a reason of at least five characters', () => {
    expect(
      correctionSchema.safeParse({ status: 'ABSENT', remarks: '', reason: 'ok' }).success,
    ).toBe(false);
    expect(
      correctionSchema.safeParse({ status: 'ABSENT', remarks: '', reason: '     ' }).success,
    ).toBe(false);
    expect(
      correctionSchema.safeParse({ status: 'ABSENT', remarks: '', reason: 'Wrong student' })
        .success,
    ).toBe(true);
  });
});
