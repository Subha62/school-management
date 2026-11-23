import { describe, expect, it } from 'vitest';
import { categorySchema, moneySchema, paymentSchema, reasonSchema, structureSchema } from './fees';

const messages = (result: { success: boolean; error?: { issues: { message: string }[] } }) =>
  result.error?.issues.map((issue) => issue.message) ?? [];

describe('moneySchema', () => {
  it.each(['1', '0.01', '1500', '1500.5', '9999999999.99'])('accepts %s', (value) => {
    expect(moneySchema.safeParse(value).success).toBe(true);
  });

  it.each([
    ['', 'Amount is required'],
    ['0', 'Amount must be more than 0'],
    ['0.00', 'Amount must be more than 0'],
    ['-5', 'Use a number with at most two decimal places'],
    ['10.123', 'Use a number with at most two decimal places'],
    ['abc', 'Use a number with at most two decimal places'],
    ['12345678901', 'Use a number with at most two decimal places'],
  ])('rejects "%s"', (value, message) => {
    expect(messages(moneySchema.safeParse(value))).toContain(message);
  });
});

describe('categorySchema', () => {
  it('needs a name', () => {
    expect(
      messages(categorySchema.safeParse({ name: ' ', description: '', active: true })),
    ).toContain('Name is required');
  });
});

describe('structureSchema', () => {
  const structure = {
    academicYearId: 'y1',
    gradeLevelId: 'g1',
    feeCategoryId: 'c1',
    description: '',
    amount: '5000',
    dueDate: '2026-12-01',
    active: true,
  };

  it('needs the year, grade and category only when creating', () => {
    const blank = { ...structure, academicYearId: '', gradeLevelId: '', feeCategoryId: '' };
    expect(messages(structureSchema(true).safeParse(blank))).toEqual([
      'Academic year is required',
      'Grade is required',
      'Fee category is required',
    ]);
    expect(structureSchema(false).safeParse(blank).success).toBe(true);
  });

  it('needs an amount and a due date', () => {
    expect(
      messages(structureSchema(true).safeParse({ ...structure, amount: '', dueDate: '' })),
    ).toEqual(expect.arrayContaining(['Amount is required', 'Due date is required']));
  });
});

describe('paymentSchema', () => {
  const payment = { amount: '1500.00', method: 'CASH', reference: '', paidOn: '2026-11-20' };

  it('accepts a payment dated today or earlier', () => {
    expect(paymentSchema('2026-11-20').safeParse(payment).success).toBe(true);
    expect(paymentSchema('2026-11-21').safeParse(payment).success).toBe(true);
  });

  it('rejects a payment dated in the future', () => {
    expect(messages(paymentSchema('2026-11-19').safeParse(payment))).toEqual([
      'The payment date cannot be in the future',
    ]);
  });

  it('rejects a missing method and an overlong reference', () => {
    expect(messages(paymentSchema('2026-11-20').safeParse({ ...payment, method: '' }))).toEqual([
      'Payment method is required',
    ]);
    expect(
      messages(paymentSchema('2026-11-20').safeParse({ ...payment, reference: 'x'.repeat(101) })),
    ).toEqual(['Reference must be at most 100 characters']);
  });
});

describe('reasonSchema', () => {
  it('needs 5 to 300 characters', () => {
    expect(messages(reasonSchema.safeParse({ reason: '' }))[0]).toBe('A reason is required');
    expect(messages(reasonSchema.safeParse({ reason: 'abcd' }))).toEqual([
      'The reason must be at least 5 characters',
    ]);
    expect(reasonSchema.safeParse({ reason: 'abcde' }).success).toBe(true);
    expect(messages(reasonSchema.safeParse({ reason: 'x'.repeat(301) }))).toEqual([
      'The reason must be at most 300 characters',
    ]);
  });
});
