import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router-dom';
import { HomeRedirect, RequireRole } from '../auth/guards';
import { AuthLayout } from '../layouts/AuthLayout';
import { PublicLayout } from '../layouts/PublicLayout';
import { RoleLayout } from '../layouts/RoleLayout';
import { NotFoundPage } from '../pages/NotFoundPage';
import { ProfilePage } from '../pages/ProfilePage';
import { UnauthorizedPage } from '../pages/UnauthorizedPage';
import { AdminDashboardPage } from '../pages/admin/AdminDashboardPage';
import { AcademicYearsPage } from '../pages/admin/AcademicYearsPage';
import { AdminReportCardPage } from '../pages/admin/AdminReportCardPage';
import { AdminResultsPage } from '../pages/admin/AdminResultsPage';
import { AdminStudentFeeDetailPage } from '../pages/admin/AdminStudentFeeDetailPage';
import { AdminStudentFeesPage } from '../pages/admin/AdminStudentFeesPage';
import { AnnouncementsPage } from '../pages/admin/AnnouncementsPage';
import { AuditLogsPage } from '../pages/admin/AuditLogsPage';
import { AttendanceReportsPage } from '../pages/admin/AttendanceReportsPage';
import { ExamDetailPage } from '../pages/admin/ExamDetailPage';
import { ExamsPage } from '../pages/admin/ExamsPage';
import { FeeCategoriesPage } from '../pages/admin/FeeCategoriesPage';
import { FeeStructuresPage } from '../pages/admin/FeeStructuresPage';
import { GradeRulesPage } from '../pages/admin/GradeRulesPage';
import { PaymentReceiptPage } from '../pages/admin/PaymentReceiptPage';
import { PaymentsPage } from '../pages/admin/PaymentsPage';
import { TimetablePage } from '../pages/admin/TimetablePage';
import { EnrollmentsPage } from '../pages/admin/EnrollmentsPage';
import { GuardiansPage } from '../pages/admin/GuardiansPage';
import { PromotionPage } from '../pages/admin/PromotionPage';
import { StudentDetailPage } from '../pages/admin/StudentDetailPage';
import { StudentsPage } from '../pages/admin/StudentsPage';
import { TeachersPage } from '../pages/admin/TeachersPage';
import { UsersPage } from '../pages/admin/UsersPage';
import { GradesPage } from '../pages/admin/GradesPage';
import { SectionsPage } from '../pages/admin/SectionsPage';
import { SubjectsPage } from '../pages/admin/SubjectsPage';
import { TeacherAssignmentsPage } from '../pages/admin/TeacherAssignmentsPage';
import { ChangePasswordPage } from '../pages/auth/ChangePasswordPage';
import { ForgotPasswordPage } from '../pages/auth/ForgotPasswordPage';
import { LoginPage } from '../pages/auth/LoginPage';
import { ResetPasswordPage } from '../pages/auth/ResetPasswordPage';
import { StudentAnnouncementsPage } from '../pages/student/StudentAnnouncementsPage';
import { StudentAssignmentDetailPage } from '../pages/student/StudentAssignmentDetailPage';
import { StudentAssignmentsPage } from '../pages/student/StudentAssignmentsPage';
import { StudentAttendancePage } from '../pages/student/StudentAttendancePage';
import { StudentDashboardPage } from '../pages/student/StudentDashboardPage';
import { StudentFeeDetailPage } from '../pages/student/StudentFeeDetailPage';
import { StudentFeesPage } from '../pages/student/StudentFeesPage';
import { StudentReceiptPage } from '../pages/student/StudentReceiptPage';
import { StudentReportCardPage } from '../pages/student/StudentReportCardPage';
import { StudentReportCardsPage } from '../pages/student/StudentReportCardsPage';
import { StudentResultsPage } from '../pages/student/StudentResultsPage';
import { StudentSubmissionsPage } from '../pages/student/StudentSubmissionsPage';
import { StudentTimetablePage } from '../pages/student/StudentTimetablePage';
import { TeacherAnnouncementsPage } from '../pages/teacher/TeacherAnnouncementsPage';
import { TeacherAssignmentDetailPage } from '../pages/teacher/TeacherAssignmentDetailPage';
import { TeacherAssignmentListPage } from '../pages/teacher/TeacherAssignmentsPage';
import { TeacherAttendancePage } from '../pages/teacher/TeacherAttendancePage';
import { TeacherCommentsPage } from '../pages/teacher/TeacherCommentsPage';
import { TeacherDashboardPage } from '../pages/teacher/TeacherDashboardPage';
import { TeacherMarksEntryPage } from '../pages/teacher/TeacherMarksEntryPage';
import { TeacherMarksPage } from '../pages/teacher/TeacherMarksPage';
import { TeacherSubmissionsPage } from '../pages/teacher/TeacherSubmissionsPage';
import { TeacherTimetablePage } from '../pages/teacher/TeacherTimetablePage';
import type { Role } from '../types/auth';

const workspaces: { role: Role; dashboard: ReactElement }[] = [
  { role: 'admin', dashboard: <AdminDashboardPage /> },
  { role: 'teacher', dashboard: <TeacherDashboardPage /> },
  { role: 'student', dashboard: <StudentDashboardPage /> },
];

/** Route guards only improve the experience; the backend authorizes every request independently. */
export function AppRoutes() {
  return (
    <Routes>
      <Route index element={<HomeRedirect />} />
      <Route element={<AuthLayout />}>
        <Route path="login" element={<LoginPage />} />
        <Route path="forgot-password" element={<ForgotPasswordPage />} />
        <Route path="reset-password" element={<ResetPasswordPage />} />
      </Route>
      <Route element={<PublicLayout />}>
        <Route path="unauthorized" element={<UnauthorizedPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
      {workspaces.map(({ role, dashboard }) => (
        <Route
          key={role}
          path={role}
          element={
            <RequireRole requiredRole={role}>
              <RoleLayout userRole={role} />
            </RequireRole>
          }
        >
          <Route index element={dashboard} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="change-password" element={<ChangePasswordPage />} />
          {role === 'admin' ? adminRoutes : null}
          {role === 'teacher' ? teacherRoutes : null}
          {role === 'student' ? studentRoutes : null}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      ))}
    </Routes>
  );
}

const teacherRoutes = (
  <>
    <Route path="timetable" element={<TeacherTimetablePage />} />
    <Route path="attendance" element={<TeacherAttendancePage />} />
    <Route path="assignments" element={<TeacherAssignmentListPage />} />
    <Route path="assignments/:id" element={<TeacherAssignmentDetailPage />} />
    <Route path="submissions" element={<TeacherSubmissionsPage />} />
    <Route path="marks" element={<TeacherMarksPage />} />
    <Route path="marks/:examId/comments/:sectionId" element={<TeacherCommentsPage />} />
    <Route path="marks/:examId/:examSubjectId/:sectionId" element={<TeacherMarksEntryPage />} />
    <Route path="announcements" element={<TeacherAnnouncementsPage />} />
  </>
);

const studentRoutes = (
  <>
    <Route path="timetable" element={<StudentTimetablePage />} />
    <Route path="attendance" element={<StudentAttendancePage />} />
    <Route path="assignments" element={<StudentAssignmentsPage />} />
    <Route path="assignments/:id" element={<StudentAssignmentDetailPage />} />
    <Route path="submissions" element={<StudentSubmissionsPage />} />
    <Route path="results" element={<StudentResultsPage />} />
    <Route path="results/:examId" element={<StudentReportCardPage />} />
    <Route path="report-cards" element={<StudentReportCardsPage />} />
    <Route path="report-cards/:examId" element={<StudentReportCardPage />} />
    <Route path="announcements" element={<StudentAnnouncementsPage />} />
    <Route path="fees" element={<StudentFeesPage />} />
    <Route path="fees/:id" element={<StudentFeeDetailPage />} />
    <Route path="fees/:id/receipts/:paymentId" element={<StudentReceiptPage />} />
  </>
);

const adminRoutes = (
  <>
    <Route path="timetable" element={<TimetablePage />} />
    <Route path="attendance-reports" element={<AttendanceReportsPage />} />
    <Route path="exams" element={<ExamsPage />} />
    <Route path="exams/:id" element={<ExamDetailPage />} />
    <Route path="grade-rules" element={<GradeRulesPage />} />
    <Route path="results" element={<AdminResultsPage />} />
    <Route path="results/:examId/:studentId" element={<AdminReportCardPage />} />
    <Route path="users" element={<UsersPage />} />
    <Route path="students" element={<StudentsPage />} />
    <Route path="students/:id" element={<StudentDetailPage />} />
    <Route path="teachers" element={<TeachersPage />} />
    <Route path="guardians" element={<GuardiansPage />} />
    <Route path="enrollments" element={<EnrollmentsPage />} />
    <Route path="promotion" element={<PromotionPage />} />
    <Route path="academic-years" element={<AcademicYearsPage />} />
    <Route path="grades" element={<GradesPage />} />
    <Route path="sections" element={<SectionsPage />} />
    <Route path="subjects" element={<SubjectsPage />} />
    <Route path="teacher-assignments" element={<TeacherAssignmentsPage />} />
    <Route path="announcements" element={<AnnouncementsPage />} />
    <Route path="fee-categories" element={<FeeCategoriesPage />} />
    <Route path="fee-structures" element={<FeeStructuresPage />} />
    <Route path="student-fees" element={<AdminStudentFeesPage />} />
    <Route path="student-fees/:id" element={<AdminStudentFeeDetailPage />} />
    <Route path="payments" element={<PaymentsPage />} />
    <Route path="payments/:id/receipt" element={<PaymentReceiptPage />} />
    <Route path="audit-logs" element={<AuditLogsPage />} />
  </>
);
