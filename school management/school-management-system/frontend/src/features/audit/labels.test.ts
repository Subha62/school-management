import { actionLabel, diffRows, entityTypeLabel, formatValue } from './labels';

describe('audit labels', () => {
  it('turns codes into readable text', () => {
    expect(actionLabel('PAYMENT_REVERSED')).toBe('Payment reversed');
    expect(actionLabel('REFRESH_TOKEN_REUSE_DETECTED')).toBe('Refresh token reuse detected');
    expect(entityTypeLabel('StudentFee')).toBe('Student fee');
    expect(entityTypeLabel('User')).toBe('User');
  });

  it('renders objects as compact JSON and gaps as a dash', () => {
    expect(formatValue({ a: [1, 2] })).toBe('{"a":[1,2]}');
    expect(formatValue('[REDACTED]')).toBe('[REDACTED]');
    expect(formatValue(null)).toBe('\u2014');
    expect(formatValue(false)).toBe('false');
  });

  it('diffs the union of keys and flags added, removed and changed fields', () => {
    const rows = diffRows({ a: 1, b: 2, c: null }, { a: 1, b: 3, d: 4 });

    expect(rows.map((row) => [row.field, row.changed])).toEqual([
      ['a', false],
      ['b', true],
      ['c', true],
      ['d', true],
    ]);
    expect(diffRows(null, null)).toEqual([]);
  });
});
