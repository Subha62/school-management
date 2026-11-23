export type DayName =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'EXCUSED';
export type SessionType = 'DAILY' | 'SUBJECT';
export type SessionStatus = 'OPEN' | 'FINALIZED';

export interface TimetableEntry {
  id: string;
  academicYearId: string;
  academicYearName: string;
  sectionId: string;
  sectionName: string;
  subjectId: string;
  subjectCode: string;
  subjectName: string;
  teacherId: string;
  teacherName: string | null;
  room: string | null;
  dayOfWeek: DayName;
  periodNumber: number;
  startTime: string;
  endTime: string;
  effectiveFrom: string;
  effectiveTo: string;
  active: boolean;
  /** The calendar date of the slot in a weekly view; null in the entry list. */
  date: string | null;
  version: number;
}

export interface TimetableWeek {
  weekStart: string;
  weekEnd: string;
  entries: TimetableEntry[];
}

/** `sectionId` is sent when creating; `version` when updating. Blank optional values are sent as null. */
export interface TimetableInput {
  sectionId?: string;
  subjectId: string;
  teacherId: string | null;
  room: string | null;
  dayOfWeek: DayName;
  periodNumber: number;
  startTime: string;
  endTime: string;
  effectiveFrom: string | null;
  effectiveTo: string | null;
  active: boolean;
  version?: number;
}

export interface AttendanceCounts {
  present: number;
  absent: number;
  late: number;
  halfDay: number;
  excused: number;
}

export interface AttendanceSummary {
  id: string;
  date: string;
  type: SessionType;
  sectionId: string;
  sectionName: string;
  subjectId: string | null;
  subjectName: string | null;
  periodNumber: number | null;
  status: SessionStatus;
  counts: AttendanceCounts;
  enrolled: number;
  finalizedAt: string | null;
  version: number;
}

export interface AttendanceEntry {
  studentId: string;
  admissionNumber: string;
  name: string;
  rollNumber: number | null;
  status: AttendanceStatus | null;
  remarks: string | null;
}

export interface AttendanceDetail {
  session: AttendanceSummary;
  entries: AttendanceEntry[];
}

export interface MarkInput {
  studentId: string;
  status: AttendanceStatus;
  remarks: string | null;
}

export interface CorrectionInput {
  studentId: string;
  status: AttendanceStatus;
  remarks: string | null;
  reason: string;
}

export interface CorrectionRecord {
  id: string;
  studentId: string;
  studentName: string;
  admissionNumber: string;
  previousStatus: AttendanceStatus | null;
  newStatus: AttendanceStatus;
  reason: string;
  correctedBy: string;
  correctedAt: string;
}

export interface ClassOption {
  sectionId: string;
  sectionName: string;
  academicYear: string;
  daily: boolean;
  subjects: { id: string; name: string }[];
}

export interface OpenSessionInput {
  sectionId: string;
  date: string;
  subjectId: string | null;
  periodNumber: number | null;
}

export interface ReportRow {
  studentId: string;
  admissionNumber: string;
  name: string;
  sectionName: string;
  present: number;
  late: number;
  halfDay: number;
  absent: number;
  excused: number;
  counted: number;
  percentage: number | null;
  belowThreshold: boolean;
}

export interface AttendanceReport {
  from: string;
  to: string;
  type: SessionType;
  threshold: number;
  truncated: boolean;
  rows: ReportRow[];
}

export interface StudentDay {
  date: string;
  type: SessionType;
  subjectName: string | null;
  periodNumber: number | null;
  status: AttendanceStatus;
  remarks: string | null;
}

export interface Rate {
  present: number;
  late: number;
  halfDay: number;
  absent: number;
  excused: number;
  percentage: number | null;
}

export interface StudentMonth {
  month: string;
  threshold: number;
  daily: Rate;
  subjectWise: Rate;
  days: StudentDay[];
}
