import type { AuditEntry } from './audit';

/** One timetable period of today. Times are `HH:mm:ss`. */
export interface DashboardSlot {
  periodNumber: number;
  startTime: string;
  endTime: string;
  subjectName: string;
  sectionName: string;
  room: string | null;
}

export interface AttendanceToday {
  registersFinalized: number;
  registersOpen: number;
}

export interface FeeSnapshot {
  billed: number;
  collected: number;
  outstanding: number;
  overdue: number;
  overdueFees: number;
}

export interface ExamSnapshot {
  openForMarks: number;
  awaitingPublication: number;
  published: number;
}

export interface AdminDashboard {
  academicYearName: string | null;
  activeStudents: number;
  activeTeachers: number;
  activeClasses: number;
  attendanceToday: AttendanceToday;
  /** `null` when there is no active academic year. */
  fees: FeeSnapshot | null;
  exams: ExamSnapshot;
  liveAnnouncements: number;
  recentActivity: AuditEntry[];
}

export interface TeacherDashboard {
  todaySlots: DashboardSlot[];
  classesTaught: number;
  awaitingReview: number;
  marksMissing: number;
  /** Classes where I am the class teacher and today's daily register is not opened yet. */
  registersToTake: string[];
  unreadAnnouncements: number;
}

export interface UpcomingWork {
  assignmentId: string;
  title: string;
  subjectName: string;
  dueAt: string;
  resubmission: boolean;
}

export interface LatestResult {
  examId: string;
  examName: string;
  percentage: number;
  grade: string | null;
  result: string | null;
}

export interface StudentDashboardFees {
  outstanding: number;
  overdueFees: number;
  nextDueDate: string | null;
}

export interface StudentDashboard {
  className: string | null;
  todaySlots: DashboardSlot[];
  /** `null` until a register has been finalized this month. */
  attendancePercent: number | null;
  upcomingWork: UpcomingWork[];
  latestResult: LatestResult | null;
  fees: StudentDashboardFees;
  unreadAnnouncements: number;
}
