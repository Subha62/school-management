import { describe, expect, it } from 'vitest';
import type { GradeRule } from '../../types/exams';
import { checkCoverage, sortByMinDescending } from './gradeScale';

const band = (grade: string, minPercent: number, maxPercent: number): GradeRule => ({
  id: grade,
  grade,
  description: null,
  minPercent,
  maxPercent,
  version: 0,
});

describe('checkCoverage', () => {
  it('accepts bands that meet one hundredth apart from 0 to 100', () => {
    const rules = [band('A', 80, 100), band('B', 60, 79.99), band('C', 0, 59.99)];
    expect(checkCoverage(rules)).toEqual({ complete: true, problems: [] });
  });

  it('reports a gap between bands', () => {
    const { complete, problems } = checkCoverage([band('A', 80, 100), band('C', 0, 59.99)]);
    expect(complete).toBe(false);
    expect(problems).toEqual(['Nothing covers 60% to 79.99%.']);
  });

  it('reports a missing start and end of the scale', () => {
    const { problems } = checkCoverage([band('B', 40, 79.99)]);
    expect(problems).toEqual(['Nothing covers 0% to 39.99%.', 'Nothing covers 80% to 100%.']);
  });

  it('asks for bands when there are none', () => {
    expect(checkCoverage([]).complete).toBe(false);
  });
});

describe('sortByMinDescending', () => {
  it('lists the highest band first without changing the input', () => {
    const rules = [band('C', 0, 59.99), band('A', 80, 100), band('B', 60, 79.99)];
    expect(sortByMinDescending(rules).map((rule) => rule.grade)).toEqual(['A', 'B', 'C']);
    expect(rules[0]?.grade).toBe('C');
  });
});
