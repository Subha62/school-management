import { useQuery } from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
import type { AdminDashboard, StudentDashboard, TeacherDashboard } from '../../types/dashboard';
import * as api from './api';

const ROOT = 'dashboard';

export const useAdminDashboard = () =>
  useQuery<AdminDashboard, ApiError>({ queryKey: [ROOT, 'admin'], queryFn: api.getAdminDashboard });

export const useTeacherDashboard = () =>
  useQuery<TeacherDashboard, ApiError>({
    queryKey: [ROOT, 'teacher'],
    queryFn: api.getTeacherDashboard,
  });

export const useStudentDashboard = () =>
  useQuery<StudentDashboard, ApiError>({
    queryKey: [ROOT, 'student'],
    queryFn: api.getStudentDashboard,
  });
