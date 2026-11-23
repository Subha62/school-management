import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type { AuditEntry, AuditQuery } from '../../types/audit';

const BASE = '/admin/audit-logs';

async function get<T>(path: string, params?: object): Promise<T> {
  const { data } = await apiClient.get<T>(path, { params });
  return data;
}

export const listAuditLogs = (params: AuditQuery) => get<PageResponse<AuditEntry>>(BASE, params);
export const listAuditActions = () => get<string[]>(`${BASE}/actions`);
export const listAuditEntityTypes = () => get<string[]>(`${BASE}/entity-types`);
export const getAuditEntry = (id: string) => get<AuditEntry>(`${BASE}/${id}`);
