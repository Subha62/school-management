/** One immutable audit trail entry. Credentials in the value maps arrive already redacted as "[REDACTED]". */
export interface AuditEntry {
  id: string;
  createdAt: string;
  action: string;
  entityType: string;
  entityId: string;
  summary: string;
  actorId: string | null;
  actorName: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  oldValues: Record<string, unknown> | null;
  newValues: Record<string, unknown> | null;
}

export type AuditSort = 'createdAt' | 'action' | 'entityType';

export interface AuditQuery {
  page: number;
  size: number;
  action?: string;
  entityType?: string;
  entityId?: string;
  actorId?: string;
  /** YYYY-MM-DD */
  from?: string;
  to?: string;
  search?: string;
  sort?: AuditSort;
  direction?: 'asc' | 'desc';
}
