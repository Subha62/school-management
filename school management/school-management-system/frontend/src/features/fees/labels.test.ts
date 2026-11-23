import { describe, expect, it } from 'vitest';
import { CURRENCY, formatMoney } from './labels';

describe('formatMoney', () => {
  const parts = (amount: number) =>
    new Intl.NumberFormat(undefined, { style: 'currency', currency: CURRENCY }).format(amount);

  it('formats amounts as currency with two decimals, exactly as returned', () => {
    expect(formatMoney(1500)).toBe(parts(1500));
    expect(formatMoney(1500.5)).toBe(parts(1500.5));
    expect(formatMoney(1500)).toMatch(/1,500\.00$/);
    expect(formatMoney(1500.5)).toMatch(/1,500\.50$/);
  });

  it('uses the school currency', () => {
    expect(CURRENCY).toBe('INR');
    expect(formatMoney(10)).toMatch(/₹|INR/);
  });

  it('formats zero and large amounts', () => {
    expect(formatMoney(0)).toMatch(/0\.00$/);
    expect(formatMoney(9999999999.99)).toMatch(/9,999,999,999\.99$/);
  });
});
