import type {
  Announcement,
  AnnouncementAction,
  AnnouncementAudience,
  AnnouncementStatus,
} from '../../types/announcements';

export const AUDIENCE_LABELS: Record<AnnouncementAudience, string> = {
  ALL: 'Everyone',
  ADMINS: 'Administrators',
  TEACHERS: 'Teachers',
  STUDENTS: 'Students',
  SECTION: 'One class',
};

export const ADMIN_AUDIENCES: AnnouncementAudience[] = [
  'ALL',
  'ADMINS',
  'TEACHERS',
  'STUDENTS',
  'SECTION',
];
/** Teachers can only write to the classes they teach. */
export const TEACHER_AUDIENCES: AnnouncementAudience[] = ['SECTION'];

export const STATUS_LABELS: Record<AnnouncementStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
};
export const STATUS_ORDER: AnnouncementStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED'];

export const audienceText = (announcement: Pick<Announcement, 'audience' | 'sectionName'>) =>
  announcement.audience === 'SECTION' && announcement.sectionName
    ? `Class ${announcement.sectionName}`
    : AUDIENCE_LABELS[announcement.audience];

export type DisplayState = 'Draft' | 'Scheduled' | 'Live' | 'Expired' | 'Archived';

/** What a manager sees: a published announcement is scheduled before its start and expired after its end. */
export function displayState(
  announcement: Pick<Announcement, 'status' | 'live' | 'publishAt'>,
  now: number = Date.now(),
): DisplayState {
  if (announcement.status === 'DRAFT') return 'Draft';
  if (announcement.status === 'ARCHIVED') return 'Archived';
  if (announcement.live) return 'Live';
  return announcement.publishAt && Date.parse(announcement.publishAt) > now
    ? 'Scheduled'
    : 'Expired';
}

export const stateTone = (state: DisplayState) =>
  state === 'Live'
    ? 'success'
    : state === 'Scheduled'
      ? 'info'
      : state === 'Draft'
        ? 'warning'
        : 'default';

export const ANNOUNCEMENT_ACTIONS: Record<
  AnnouncementAction,
  { title: string; message: string; label: string; done: string; destructive?: boolean }
> = {
  publish: {
    title: 'Publish this announcement?',
    message:
      'The audience can read it from its start time until it expires. Once published, the audience can no longer be changed.',
    label: 'Publish',
    done: 'Announcement published',
  },
  archive: {
    title: 'Archive this announcement?',
    message: 'It disappears from every inbox. An archived announcement cannot be changed.',
    label: 'Archive',
    done: 'Announcement archived',
    destructive: true,
  },
};
