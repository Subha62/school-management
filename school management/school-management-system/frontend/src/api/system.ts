import type { SystemInfo } from '../types/api';
import { apiClient } from './client';

export async function fetchSystemInfo(): Promise<SystemInfo> {
  const response = await apiClient.get<SystemInfo>('/system/info');
  return response.data;
}
