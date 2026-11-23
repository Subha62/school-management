import type { AuditEntry } from '../../types/audit';

/** `PAYMENT_REVERSED` -> `Payment reversed`; `StudentFee` -> `Student fee`. */
export function humanize(code: string): string {
  const words = code
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_\-\s]+/g, ' ')
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const actionLabel = (action: string): string => humanize(action);
export const entityTypeLabel = (entityType: string): string => humanize(entityType);

export const actorLabel = (entry: Pick<AuditEntry, 'actorName'>): string =>
  entry.actorName?.trim() || 'System/unknown';

/** Scalars as plain text, objects and lists as compact JSON; missing values as an em dash. */
export function formatValue(value: unknown): string {
  if (value === undefined || value === null) {
    return '\u2014';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

export interface ChangeRow {
  field: string;
  before: string;
  after: string;
  changed: boolean;
}

/** The union of keys of both snapshots, in first-seen order; a row is changed when its values differ. */
export function diffRows(
  oldValues: Record<string, unknown> | null,
  newValues: Record<string, unknown> | null,
): ChangeRow[] {
  const before = oldValues ?? {};
  const after = newValues ?? {};
  const fields = [...new Set([...Object.keys(before), ...Object.keys(after)])];
  return fields.map((field) => {
    const left = formatValue(before[field]);
    const right = formatValue(after[field]);
    const present = field in before !== field in after;
    return { field, before: left, after: right, changed: present || left !== right };
  });
}
