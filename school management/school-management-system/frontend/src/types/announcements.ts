export type AnnouncementAudience = 'ALL' | 'ADMINS' | 'TEACHERS' | 'STUDENTS' | 'SECTION';
export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type AnnouncementAction = 'publish' | 'archive';
/** Who is looking: administrators and teachers manage announcements through different base paths. */
export type AnnouncementManager = 'admin' | 'teacher';

export interface Announcement {
  id: string;
  title: string;
  body: string;
  audience: AnnouncementAudience;
  sectionId: string | null;
  sectionName: string | null;
  status: AnnouncementStatus;
  publishAt: string | null;
  expiresAt: string | null;
  publishedAt: string | null;
  authorName: string;
  createdAt: string;
  /** Only filled for people who manage announcements; null in a reader's feed. */
  readCount: number | null;
  read: boolean;
  /** Whether the announcement is visible to its audience right now. */
  live: boolean;
  version: number;
}

export interface UnreadCount {
  count: number;
}

/** `sectionId` is sent only for the SECTION audience; `version` only when updating. */
export interface AnnouncementInput {
  title: string;
  body: string;
  audience: AnnouncementAudience;
  sectionId?: string | null;
  publishAt?: string | null;
  expiresAt?: string | null;
  version?: number;
}

export interface FeedQuery {
  page: number;
  size: number;
  unread?: boolean;
}

export interface ManageQuery {
  page: number;
  size: number;
  status?: AnnouncementStatus;
  audience?: AnnouncementAudience;
  search?: string;
}
