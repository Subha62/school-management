import { apiClient } from '../../api/client';
import type { PageResponse } from '../../types/api';
import type {
  Announcement,
  AnnouncementAction,
  AnnouncementInput,
  AnnouncementManager,
  FeedQuery,
  ManageQuery,
  UnreadCount,
} from '../../types/announcements';

const manageBase = (manager: AnnouncementManager) => `/${manager}/announcements`;

// ---- reading (every role) ----
export async function listFeed(params: FeedQuery): Promise<PageResponse<Announcement>> {
  const { data } = await apiClient.get<PageResponse<Announcement>>('/announcements', {
    params: { page: params.page, size: params.size, unread: params.unread ? true : undefined },
  });
  return data;
}

export async function getUnreadCount(): Promise<UnreadCount> {
  const { data } = await apiClient.get<UnreadCount>('/announcements/unread-count');
  return data;
}

export const markRead = async (id: string): Promise<void> => {
  await apiClient.post(`/announcements/${id}/read`);
};

// ---- writing (administrators and teachers) ----
export async function listManaged(
  manager: AnnouncementManager,
  params: ManageQuery,
): Promise<PageResponse<Announcement>> {
  const { data } = await apiClient.get<PageResponse<Announcement>>(manageBase(manager), {
    params,
  });
  return data;
}

export async function saveAnnouncement(
  manager: AnnouncementManager,
  id: string | undefined,
  body: AnnouncementInput,
): Promise<Announcement> {
  const { data } = id
    ? await apiClient.put<Announcement>(`${manageBase(manager)}/${id}`, body)
    : await apiClient.post<Announcement>(manageBase(manager), body);
  return data;
}

export const deleteAnnouncement = async (
  manager: AnnouncementManager,
  id: string,
): Promise<void> => {
  await apiClient.delete(`${manageBase(manager)}/${id}`);
};

export async function transitionAnnouncement(
  manager: AnnouncementManager,
  id: string,
  action: AnnouncementAction,
): Promise<Announcement> {
  const { data } = await apiClient.post<Announcement>(`${manageBase(manager)}/${id}/${action}`);
  return data;
}
