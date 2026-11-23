import AssessmentIcon from '@mui/icons-material/Assessment';
import AssignmentIcon from '@mui/icons-material/Assignment';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import CampaignIcon from '@mui/icons-material/Campaign';
import ClassIcon from '@mui/icons-material/Class';
import DashboardIcon from '@mui/icons-material/Dashboard';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import FamilyRestroomIcon from '@mui/icons-material/FamilyRestroom';
import GradingIcon from '@mui/icons-material/Grading';
import GroupsIcon from '@mui/icons-material/Groups';
import HistoryIcon from '@mui/icons-material/History';
import LayersIcon from '@mui/icons-material/Layers';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import PaymentsIcon from '@mui/icons-material/Payments';
import PersonIcon from '@mui/icons-material/Person';
import PeopleIcon from '@mui/icons-material/People';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import SchoolIcon from '@mui/icons-material/School';
import ScheduleIcon from '@mui/icons-material/Schedule';
import SchemaIcon from '@mui/icons-material/Schema';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import type { SvgIconComponent } from '@mui/icons-material';
import type { Role } from '../types/auth';

export type { Role };

export interface NavItem {
  label: string;
  /** Path relative to the role's base path. An empty string is the role's dashboard. */
  path: string;
  icon: SvgIconComponent;
  /** False until the feature ships; the item is shown disabled so the final structure is visible. */
  available: boolean;
  /** Shows the number of unread announcements beside the label. */
  badge?: 'unread-announcements';
}

export interface NavGroup {
  title?: string;
  items: NavItem[];
}

export interface RoleNavigation {
  title: string;
  basePath: string;
  groups: NavGroup[];
}

const planned = (label: string, path: string, icon: SvgIconComponent): NavItem => ({
  label,
  path,
  icon,
  available: false,
});

const ready = (label: string, path: string, icon: SvgIconComponent): NavItem => ({
  label,
  path,
  icon,
  available: true,
});

const announcements = (): NavItem => ({
  ...ready('Announcements', 'announcements', CampaignIcon),
  badge: 'unread-announcements',
});

const dashboard: NavItem = { label: 'Dashboard', path: '', icon: DashboardIcon, available: true };
const profile: NavItem = { label: 'Profile', path: 'profile', icon: PersonIcon, available: true };

export const navigationByRole: Record<Role, RoleNavigation> = {
  admin: {
    title: 'Administration',
    basePath: '/admin',
    groups: [
      { items: [dashboard, profile] },
      {
        title: 'People',
        items: [
          ready('Users', 'users', PeopleIcon),
          ready('Students', 'students', SchoolIcon),
          ready('Teachers', 'teachers', PersonIcon),
          ready('Guardians', 'guardians', FamilyRestroomIcon),
        ],
      },
      {
        title: 'Academics',
        items: [
          ready('Academic years', 'academic-years', CalendarMonthIcon),
          ready('Grades', 'grades', LayersIcon),
          ready('Sections', 'sections', GroupsIcon),
          ready('Subjects', 'subjects', MenuBookIcon),
          ready('Teacher assignments', 'teacher-assignments', SchemaIcon),
          ready('Enrollments', 'enrollments', FactCheckIcon),
          ready('Student promotion', 'promotion', TrendingUpIcon),
        ],
      },
      {
        title: 'Operations',
        items: [
          ready('Timetable', 'timetable', ScheduleIcon),
          ready('Attendance reports', 'attendance-reports', EventAvailableIcon),
          announcements(),
        ],
      },
      {
        title: 'Examinations',
        items: [
          ready('Exams', 'exams', AssignmentIcon),
          ready('Grade rules', 'grade-rules', GradingIcon),
          ready('Results', 'results', AssessmentIcon),
        ],
      },
      {
        title: 'Finance',
        items: [
          ready('Fee categories', 'fee-categories', PaymentsIcon),
          ready('Fee structures', 'fee-structures', PaymentsIcon),
          ready('Student fees', 'student-fees', PaymentsIcon),
          ready('Payments', 'payments', ReceiptLongIcon),
        ],
      },
      { title: 'System', items: [ready('Audit logs', 'audit-logs', HistoryIcon)] },
    ],
  },
  teacher: {
    title: 'Teaching',
    basePath: '/teacher',
    groups: [
      {
        items: [
          dashboard,
          ready('My timetable', 'timetable', ScheduleIcon),
          planned('My classes', 'classes', ClassIcon),
          planned('Student list', 'students', SchoolIcon),
          ready('Attendance', 'attendance', EventAvailableIcon),
          ready('Assignments', 'assignments', AssignmentIcon),
          ready('Submission review', 'submissions', FactCheckIcon),
          ready('Exams and marks', 'marks', GradingIcon),
          announcements(),
          profile,
        ],
      },
    ],
  },
  student: {
    title: 'Student',
    basePath: '/student',
    groups: [
      {
        items: [
          dashboard,
          ready('My timetable', 'timetable', ScheduleIcon),
          ready('Attendance', 'attendance', EventAvailableIcon),
          ready('Assignments', 'assignments', AssignmentIcon),
          ready('Submissions', 'submissions', FactCheckIcon),
          ready('Exams and results', 'results', AssessmentIcon),
          ready('Report cards', 'report-cards', GradingIcon),
          announcements(),
          ready('Fees', 'fees', PaymentsIcon),
          profile,
        ],
      },
    ],
  },
};
