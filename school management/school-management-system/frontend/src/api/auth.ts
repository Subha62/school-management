import type {
  ChangePasswordPayload,
  CurrentUser,
  LoginPayload,
  ResetPasswordPayload,
} from '../types/auth';
import { apiClient } from './client';

export async function login(payload: LoginPayload): Promise<CurrentUser> {
  const response = await apiClient.post<CurrentUser>('/auth/login', payload);
  return response.data;
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const response = await apiClient.get<CurrentUser>('/auth/me');
  return response.data;
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout');
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.post('/auth/change-password', payload);
}

export async function forgotPassword(email: string): Promise<void> {
  await apiClient.post('/auth/forgot-password', { email });
}

export async function resetPassword(payload: ResetPasswordPayload): Promise<void> {
  await apiClient.post('/auth/reset-password', payload);
}
