import type { GradeRule } from '../../types/exams';
import { formatNumber } from './labels';

export interface Coverage {
  complete: boolean;
  /** What is missing, in the order it appears on the 0 to 100 scale. */
  problems: string[];
}

const cents = (value: number) => Math.round(value * 100);
const percent = (value: number) => `${formatNumber(value)}%`;

/** The scale shown to people: the highest band first. */
export const sortByMinDescending = (rules: GradeRule[]): GradeRule[] =>
  [...rules].sort((a, b) => b.minPercent - a.minPercent);

/**
 * Whether the bands cover 0 to 100 without gaps. The next band must start one hundredth above the previous
 * band's end. This is only a hint for display; the server decides what is valid.
 */
export function checkCoverage(rules: GradeRule[]): Coverage {
  if (rules.length === 0) {
    return {
      complete: false,
      problems: ['No bands yet. Add bands so that 0% to 100% is covered.'],
    };
  }
  const ascending = [...rules].sort((a, b) => a.minPercent - b.minPercent);
  const problems: string[] = [];
  const first = ascending[0]!;
  if (cents(first.minPercent) > 0) {
    problems.push(`Nothing covers 0% to ${percent(first.minPercent - 0.01)}.`);
  }
  for (let index = 1; index < ascending.length; index += 1) {
    const previous = ascending[index - 1]!;
    const current = ascending[index]!;
    if (cents(current.minPercent) > cents(previous.maxPercent) + 1) {
      problems.push(
        `Nothing covers ${percent(previous.maxPercent + 0.01)} to ${percent(current.minPercent - 0.01)}.`,
      );
    }
  }
  const last = ascending.reduce((best, rule) => (rule.maxPercent > best.maxPercent ? rule : best));
  if (cents(last.maxPercent) < 10000) {
    problems.push(`Nothing covers ${percent(last.maxPercent + 0.01)} to 100%.`);
  }
  return { complete: problems.length === 0, problems };
}
