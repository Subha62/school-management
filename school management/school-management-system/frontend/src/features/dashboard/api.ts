import { apiClient } from '../../api/client';
import type { AdminDashboard, StudentDashboard, TeacherDashboard } from '../../types/dashboard';

async function get<T>(path: string): Promise<T> {
  const { data } = await apiClient.get<T>(path);
  return data;
}

export const getAdminDashboard = () => get<AdminDashboard>('/admin/dashboard');
export const getTeacherDashboard = () => get<TeacherDashboard>('/teacher/dashboard');
export const getStudentDashboard = () => get<StudentDashboard>('/student/dashboard');
