import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { PageResponse } from '../../types/api';
import type { AuditEntry, AuditQuery } from '../../types/audit';
import * as api from './api';

const ROOT = 'audit';

export const useAuditLogs = (params: AuditQuery) =>
  useQuery<PageResponse<AuditEntry>, ApiError>({
    queryKey: [ROOT, 'list', params],
    queryFn: () => api.listAuditLogs(params),
    placeholderData: keepPreviousData,
  });

export const useAuditActions = () =>
  useQuery<string[], ApiError>({ queryKey: [ROOT, 'actions'], queryFn: api.listAuditActions });

export const useAuditEntityTypes = () =>
  useQuery<string[], ApiError>({
    queryKey: [ROOT, 'entity-types'],
    queryFn: api.listAuditEntityTypes,
  });
