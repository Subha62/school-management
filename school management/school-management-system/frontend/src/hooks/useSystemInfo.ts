import { useQuery } from '@tanstack/react-query';
import { fetchSystemInfo } from '../api/system';
import type { ApiError } from '../api/errors';
import type { SystemInfo } from '../types/api';

export function useSystemInfo() {
  return useQuery<SystemInfo, ApiError>({
    queryKey: ['system', 'info'],
    queryFn: fetchSystemInfo,
  });
}
