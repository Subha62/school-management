import type { PageResponse } from '../types/api';
import type {
  AcademicYear,
  GradeLevel,
  Section,
  SectionSubject,
  Subject,
  TeacherOption,
} from '../types/academics';
import type {
  AdminUser,
  Enrollment,
  Guardian,
  Student,
  StudentGuardianLink,
} from '../types/people';
import type {
  Exam,
  GradeRule,
  MarkRow,
  MarksSheet,
  ReportCard,
  StudentResult,
  SubjectResult,
} from '../types/exams';
import type { Announcement } from '../types/announcements';
import type {
  Collections,
  FeeCategory,
  FeeStructure,
  FeeSummary,
  Payment,
  Receipt,
  StudentFee,
} from '../types/fees';
import type { ClassOption } from '../types/operations';
import type { AuditEntry } from '../types/audit';
import type {
  AdminDashboard,
  DashboardSlot,
  StudentDashboard,
  TeacherDashboard,
} from '../types/dashboard';

export const makeStudent = (overrides: Partial<Student> = {}): Student => ({
  id: 'student-1',
  userId: 'user-s1',
  admissionNumber: 'S-100',
  firstName: 'Sam',
  lastName: 'Student',
  email: 'sam@school.test',
  phoneNumber: null,
  dateOfBirth: '2012-04-10',
  gender: 'MALE',
  admissionDate: '2026-06-01',
  bloodGroup: null,
  addressLine1: '1 Main Road',
  addressLine2: null,
  city: 'Pune',
  state: null,
  postalCode: null,
  emergencyContactName: 'Parent',
  emergencyContactPhone: '+91 98765 00000',
  status: 'ACTIVE',
  currentEnrollment: null,
  version: 0,
  ...overrides,
});

export const makeEnrollment = (overrides: Partial<Enrollment> = {}): Enrollment => ({
  id: 'enrollment-1',
  student: { id: 'student-1', admissionNumber: 'S-100', name: 'Sam Student' },
  academicYear: { id: 'year-1', name: '2026-2027' },
  gradeLevel: { id: 'grade-1', name: 'Grade 8' },
  section: { id: 'section-1', name: 'A' },
  rollNumber: 1,
  enrollmentDate: '2026-06-01',
  status: 'ACTIVE',
  sourceEnrollmentId: null,
  promotionBatchId: null,
  version: 0,
  ...overrides,
});

export const makeGuardian = (overrides: Partial<Guardian> = {}): Guardian => ({
  id: 'guardian-1',
  firstName: 'Maya',
  lastName: 'Parent',
  relationship: 'MOTHER',
  email: null,
  phoneNumber: '+91 90000 11111',
  occupation: null,
  address: null,
  emergencyContact: true,
  studentCount: 0,
  version: 0,
  ...overrides,
});

export const makeGuardianLink = (
  overrides: Partial<StudentGuardianLink> = {},
): StudentGuardianLink => ({
  id: 'link-1',
  guardianId: 'guardian-1',
  firstName: 'Maya',
  lastName: 'Parent',
  relationship: 'MOTHER',
  phoneNumber: '+91 90000 11111',
  email: null,
  primaryGuardian: true,
  receivesNotifications: true,
  authorizedPickup: false,
  version: 0,
  ...overrides,
});

export const makeAdminUser = (overrides: Partial<AdminUser> = {}): AdminUser => ({
  id: 'user-9',
  username: 'T-100',
  email: 'tara@school.test',
  firstName: 'Tara',
  lastName: 'Teacher',
  phoneNumber: null,
  roles: ['ROLE_TEACHER'],
  accountStatus: 'ACTIVE',
  temporarilyLocked: false,
  lastLoginAt: null,
  version: 3,
  ...overrides,
});

export function pageOf<T>(content: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    first: true,
    last: true,
    ...overrides,
  };
}

export const makeYear = (overrides: Partial<AcademicYear> = {}): AcademicYear => ({
  id: 'year-1',
  name: '2026-2027',
  startDate: '2026-06-01',
  endDate: '2027-03-31',
  status: 'PLANNED',
  version: 0,
  ...overrides,
});

export const makeGrade = (overrides: Partial<GradeLevel> = {}): GradeLevel => ({
  id: 'grade-1',
  name: 'Grade 8',
  displayOrder: 8,
  active: true,
  version: 0,
  ...overrides,
});

export const makeSubject = (overrides: Partial<Subject> = {}): Subject => ({
  id: 'subject-1',
  code: 'MATH',
  name: 'Mathematics',
  description: null,
  active: true,
  version: 0,
  ...overrides,
});

export const makeSection = (overrides: Partial<Section> = {}): Section => ({
  id: 'section-1',
  academicYear: { id: 'year-1', name: '2026-2027' },
  gradeLevel: { id: 'grade-1', name: 'Grade 8' },
  name: 'A',
  capacity: 40,
  classTeacher: null,
  active: true,
  version: 0,
  ...overrides,
});

export const makeAssignment = (overrides: Partial<SectionSubject> = {}): SectionSubject => ({
  id: 'assignment-1',
  academicYearId: 'year-1',
  academicYearName: '2026-2027',
  sectionId: 'section-1',
  sectionName: 'A',
  gradeLevelName: 'Grade 8',
  subject: { id: 'subject-1', code: 'MATH', name: 'Mathematics' },
  teacher: null,
  weeklyPeriods: 5,
  active: true,
  version: 0,
  ...overrides,
});

export const makeTeacher = (overrides: Partial<TeacherOption> = {}): TeacherOption => ({
  id: 'teacher-1',
  firstName: 'Tara',
  lastName: 'Teacher',
  email: 'tara@school.test',
  ...overrides,
});

export const makeGradeRule = (overrides: Partial<GradeRule> = {}): GradeRule => ({
  id: 'rule-1',
  grade: 'A',
  description: 'Excellent',
  minPercent: 80,
  maxPercent: 100,
  version: 0,
  ...overrides,
});

export const makeExam = (overrides: Partial<Exam> = {}): Exam => ({
  id: 'exam-1',
  academicYearId: 'year-1',
  academicYearName: '2026-2027',
  gradeLevelId: 'grade-1',
  gradeLevelName: 'Grade 8',
  name: 'Term 1',
  startDate: null,
  endDate: null,
  status: 'DRAFT',
  subjects: [
    {
      id: 'es-1',
      subjectId: 'subject-1',
      subjectCode: 'MATH',
      subjectName: 'Mathematics',
      maxMarks: 100,
      passMarks: 35,
      examDate: null,
    },
  ],
  lockedAt: null,
  publishedAt: null,
  version: 0,
  ...overrides,
});

export const makeMarkRow = (overrides: Partial<MarkRow> = {}): MarkRow => ({
  studentId: 'st-1',
  admissionNumber: 'S-1',
  studentName: 'Ann Pupil',
  rollNumber: 1,
  marks: null,
  absent: false,
  remarks: null,
  entered: false,
  ...overrides,
});

export const makeMarksSheet = (overrides: Partial<MarksSheet> = {}): MarksSheet => ({
  examId: 'exam-1',
  examName: 'Term 1',
  status: 'OPEN_FOR_MARKS',
  examSubjectId: 'es-1',
  subjectName: 'Mathematics',
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  maxMarks: 100,
  passMarks: 35,
  editable: true,
  rows: [
    makeMarkRow(),
    makeMarkRow({
      studentId: 'st-2',
      admissionNumber: 'S-2',
      studentName: 'Bob Pupil',
      rollNumber: 2,
    }),
  ],
  ...overrides,
});

export const makeSubjectResult = (overrides: Partial<SubjectResult> = {}): SubjectResult => ({
  subjectId: 'subject-1',
  subjectCode: 'MATH',
  subjectName: 'Mathematics',
  maxMarks: 100,
  passMarks: 35,
  marks: 90,
  absent: false,
  percentage: 90,
  grade: 'A',
  status: 'PASS',
  remarks: null,
  ...overrides,
});

export const makeStudentResult = (overrides: Partial<StudentResult> = {}): StudentResult => ({
  studentId: 'st-1',
  admissionNumber: 'S-1',
  studentName: 'Ann Pupil',
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  rollNumber: 1,
  subjects: [
    makeSubjectResult(),
    makeSubjectResult({
      subjectId: 'subject-2',
      subjectCode: 'ENG',
      subjectName: 'English',
      maxMarks: 50,
      passMarks: 20,
      marks: 10,
      percentage: 20,
      grade: 'F',
      status: 'FAIL',
    }),
  ],
  totalMarks: 100,
  totalMax: 150,
  percentage: 66.67,
  grade: 'B',
  result: 'FAIL',
  comment: 'Work on English',
  ...overrides,
});

export const makeReportCard = (overrides: Partial<ReportCard> = {}): ReportCard => ({
  examId: 'exam-1',
  examName: 'Term 1',
  schoolName: 'Demo School',
  academicYearName: '2026-2027',
  gradeLevelName: 'Grade 8',
  startDate: null,
  endDate: null,
  publishedAt: '2026-11-18T10:00:00Z',
  result: makeStudentResult(),
  ...overrides,
});

// ---- announcements ----
export const makeAnnouncement = (overrides: Partial<Announcement> = {}): Announcement => ({
  id: 'ann-1',
  title: 'Sports day',
  body: 'Bring your kit.\nSecond line',
  audience: 'ALL',
  sectionId: null,
  sectionName: null,
  status: 'PUBLISHED',
  publishAt: null,
  expiresAt: null,
  publishedAt: '2026-11-18T10:00:00Z',
  authorName: 'Alice Admin',
  createdAt: '2026-11-18T09:00:00Z',
  readCount: null,
  read: false,
  live: true,
  version: 0,
  ...overrides,
});

export const makeClassOption = (overrides: Partial<ClassOption> = {}): ClassOption => ({
  sectionId: 'section-1',
  sectionName: 'Grade 8 - A',
  academicYear: '2026-2027',
  daily: true,
  subjects: [{ id: 'subject-1', name: 'Mathematics' }],
  ...overrides,
});

// ---- fees ----
export const makeFeeCategory = (overrides: Partial<FeeCategory> = {}): FeeCategory => ({
  id: 'cat-1',
  name: 'Tuition',
  description: null,
  active: true,
  version: 0,
  ...overrides,
});

export const makeFeeStructure = (overrides: Partial<FeeStructure> = {}): FeeStructure => ({
  id: 'fs-1',
  academicYearId: 'year-1',
  academicYearName: '2026-2027',
  gradeLevelId: 'grade-1',
  gradeLevelName: 'Grade 8',
  feeCategoryId: 'cat-1',
  categoryName: 'Tuition',
  description: 'Term 1',
  amount: 5000,
  dueDate: '2026-12-01',
  active: true,
  billedStudents: 0,
  version: 0,
  ...overrides,
});

export const makeStudentFee = (overrides: Partial<StudentFee> = {}): StudentFee => ({
  id: 'fee-1',
  studentId: 'student-1',
  admissionNumber: 'S-100',
  studentName: 'Sam Student',
  className: 'Grade 8 - A',
  academicYearId: 'year-1',
  academicYearName: '2026-2027',
  structureId: 'fs-1',
  categoryName: 'Tuition',
  description: 'Term 1',
  amount: 5000,
  paidAmount: 3500,
  balance: 1500,
  dueDate: '2026-12-01',
  status: 'PARTIALLY_PAID',
  waiveReason: null,
  waivedAt: null,
  version: 0,
  ...overrides,
});

export const makePayment = (overrides: Partial<Payment> = {}): Payment => ({
  id: 'pay-1',
  receiptNumber: 'RCPT-0001',
  studentFeeId: 'fee-1',
  studentId: 'student-1',
  admissionNumber: 'S-100',
  studentName: 'Sam Student',
  categoryName: 'Tuition',
  amount: 3500,
  method: 'CASH',
  reference: null,
  paidOn: '2026-11-10',
  receivedByName: 'Alice Admin',
  createdAt: '2026-11-10T09:00:00Z',
  reversed: false,
  reversalReason: null,
  reversedAt: null,
  reversedByName: null,
  ...overrides,
});

export const makeReceipt = (overrides: Partial<Receipt> = {}): Receipt => ({
  receiptNumber: 'RCPT-0001',
  schoolName: 'Demo School',
  studentName: 'Sam Student',
  admissionNumber: 'S-100',
  className: 'Grade 8 - A',
  academicYearName: '2026-2027',
  categoryName: 'Tuition',
  description: 'Term 1',
  feeAmount: 5000,
  amount: 3500,
  method: 'UPI',
  reference: 'UPI-778',
  paidOn: '2026-11-10',
  receivedByName: 'Alice Admin',
  balance: 1500,
  reversed: false,
  reversalReason: null,
  reversedAt: null,
  ...overrides,
});

export const makeFeeSummary = (overrides: Partial<FeeSummary> = {}): FeeSummary => ({
  academicYearId: 'year-1',
  gradeLevelId: null,
  billed: 50000,
  collected: 35000,
  outstanding: 15000,
  overdue: 4000,
  waived: 1000,
  countByStatus: { PAID: 3, PARTIALLY_PAID: 2 },
  byCategory: [{ categoryName: 'Tuition', billed: 50000, collected: 35000, outstanding: 15000 }],
  ...overrides,
});

export const makeCollections = (overrides: Partial<Collections> = {}): Collections => ({
  from: null,
  to: null,
  payments: 4,
  received: 20000,
  reversed: 2000,
  net: 18000,
  byMethod: [{ method: 'CASH', count: 3, total: 12000 }],
  ...overrides,
});

export const makeAuditEntry = (overrides: Partial<AuditEntry> = {}): AuditEntry => ({
  id: 'audit-1',
  createdAt: '2026-11-19T10:15:00Z',
  action: 'PAYMENT_REVERSED',
  entityType: 'Payment',
  entityId: 'pay-1',
  summary: 'Reversed receipt R-0001',
  actorId: 'user-a1',
  actorName: 'Alice Admin',
  ipAddress: '10.0.0.5',
  userAgent: 'Mozilla/5.0',
  oldValues: null,
  newValues: null,
  ...overrides,
});

export const makeSlot = (overrides: Partial<DashboardSlot> = {}): DashboardSlot => ({
  periodNumber: 1,
  startTime: '09:00:00',
  endTime: '09:45:00',
  subjectName: 'Mathematics',
  sectionName: 'Grade 8 - A',
  room: 'R12',
  ...overrides,
});

export const makeAdminDashboard = (overrides: Partial<AdminDashboard> = {}): AdminDashboard => ({
  academicYearName: '2026-2027',
  activeStudents: 420,
  activeTeachers: 35,
  activeClasses: 14,
  attendanceToday: { registersFinalized: 9, registersOpen: 3 },
  fees: { billed: 50000, collected: 35000, outstanding: 15000, overdue: 4000, overdueFees: 6 },
  exams: { openForMarks: 2, awaitingPublication: 1, published: 4 },
  liveAnnouncements: 3,
  recentActivity: [makeAuditEntry()],
  ...overrides,
});

export const makeTeacherDashboard = (
  overrides: Partial<TeacherDashboard> = {},
): TeacherDashboard => ({
  todaySlots: [makeSlot()],
  classesTaught: 5,
  awaitingReview: 7,
  marksMissing: 12,
  registersToTake: ['Grade 8 - A'],
  unreadAnnouncements: 2,
  ...overrides,
});

export const makeStudentDashboard = (
  overrides: Partial<StudentDashboard> = {},
): StudentDashboard => ({
  className: 'Grade 8 - A',
  todaySlots: [makeSlot()],
  attendancePercent: 92.5,
  upcomingWork: [
    {
      assignmentId: 'asg-1',
      title: 'Fractions worksheet',
      subjectName: 'Mathematics',
      dueAt: '2026-11-25T16:00:00Z',
      resubmission: true,
    },
  ],
  latestResult: {
    examId: 'exam-1',
    examName: 'Term 1 exam',
    percentage: 78.5,
    grade: 'B',
    result: 'PASS',
  },
  fees: { outstanding: 1500, overdueFees: 1, nextDueDate: '2026-12-05' },
  unreadAnnouncements: 4,
  ...overrides,
});
