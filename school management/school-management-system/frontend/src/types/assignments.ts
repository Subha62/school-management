export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED';
export type SubmissionStatus = 'SUBMITTED' | 'LATE' | 'REVIEWED' | 'RESUBMISSION_REQUESTED';

export interface Attachment {
  id: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface AssignmentCounts {
  enrolled: number;
  submitted: number;
  awaitingReview: number;
  reviewed: number;
}

/** An assignment as its teacher sees it. */
export interface Assignment {
  id: string;
  sectionId: string;
  sectionName: string;
  academicYearName: string;
  subjectId: string;
  subjectName: string;
  teacherId: string;
  teacherName: string | null;
  title: string;
  description: string | null;
  dueAt: string;
  maxMarks: number;
  allowLate: boolean;
  status: AssignmentStatus;
  publishedAt: string | null;
  closedAt: string | null;
  attachments: Attachment[];
  counts: AssignmentCounts;
  version: number;
}

/** `sectionId` and `subjectId` are sent when creating; `version` when updating. */
export interface AssignmentInput {
  sectionId?: string;
  subjectId?: string;
  title: string;
  description: string | null;
  dueAt: string;
  maxMarks: number;
  allowLate: boolean;
  version?: number;
}

export interface Submission {
  id: string;
  assignmentId: string;
  studentId: string;
  admissionNumber: string;
  studentName: string;
  status: SubmissionStatus;
  textAnswer: string | null;
  submittedAt: string;
  /** Hidden from students until the work is reviewed. */
  marks: number | null;
  feedback: string | null;
  reviewedAt: string | null;
  reviewedBy: string | null;
  attachments: Attachment[];
  version: number;
}

export interface RosterRow {
  studentId: string;
  admissionNumber: string;
  studentName: string;
  rollNumber: number | null;
  submissionId: string | null;
  status: SubmissionStatus | null;
  submittedAt: string | null;
  marks: number | null;
}

export interface StudentAssignment {
  id: string;
  subjectName: string;
  sectionName: string;
  teacherName: string | null;
  title: string;
  description: string | null;
  dueAt: string;
  maxMarks: number;
  allowLate: boolean;
  status: AssignmentStatus;
  attachments: Attachment[];
  submission: Submission | null;
  canSubmit: boolean;
}

export interface SubmissionListItem {
  id: string;
  assignmentId: string;
  assignmentTitle: string;
  subjectName: string;
  sectionName: string;
  studentId: string;
  admissionNumber: string;
  studentName: string;
  status: SubmissionStatus;
  submittedAt: string;
  dueAt: string;
  marks: number | null;
  maxMarks: number;
}

export type ReviewOutcome = 'REVIEWED' | 'RESUBMISSION_REQUESTED';

export interface ReviewInput {
  outcome: ReviewOutcome;
  marks: number | null;
  feedback: string | null;
  version: number;
}
