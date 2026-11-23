import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
} from '@tanstack/react-query';
import type { ApiError } from '../../api/errors';
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
import type { Section } from '../../types/academics';
import * as academics from '../academics/api';
import * as api from './api';

/** Every announcement query shares this root, so one change refreshes feeds, lists and the unread count. */
const ROOT = 'announcements';

const UNREAD_POLL_MS = 60_000;

export const useFeed = (params: FeedQuery) =>
  useQuery<PageResponse<Announcement>, ApiError>({
    queryKey: [ROOT, 'feed', params],
    queryFn: () => api.listFeed(params),
    placeholderData: keepPreviousData,
  });

/** Polled so the badge in the navigation stays current while the tab is open. */
export const useUnreadCount = () =>
  useQuery<UnreadCount, ApiError>({
    queryKey: [ROOT, 'unread-count'],
    queryFn: api.getUnreadCount,
    refetchInterval: UNREAD_POLL_MS,
  });

export const useManagedAnnouncements = (manager: AnnouncementManager, params: ManageQuery) =>
  useQuery<PageResponse<Announcement>, ApiError>({
    queryKey: [ROOT, 'managed', manager, params],
    queryFn: () => api.listManaged(manager, params),
    placeholderData: keepPreviousData,
  });

/** The classes an administrator can address (the first 100 active classes). */
export const useAdminClassOptions = (enabled: boolean) =>
  useQuery<PageResponse<Section>, ApiError>({
    queryKey: [ROOT, 'class-options'],
    queryFn: () => academics.listSections({ page: 0, size: 100, active: true }),
    enabled,
  });

function useAnnouncementMutation<TVars, TResult>(
  mutationFn: (vars: TVars) => Promise<TResult>,
): UseMutationResult<TResult, ApiError, TVars> {
  const queryClient = useQueryClient();
  return useMutation<TResult, ApiError, TVars>({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [ROOT] }),
  });
}

export const useMarkRead = () => useAnnouncementMutation((id: string) => api.markRead(id));

export const useSaveAnnouncement = (manager: AnnouncementManager) =>
  useAnnouncementMutation((vars: { id?: string; body: AnnouncementInput }) =>
    api.saveAnnouncement(manager, vars.id, vars.body),
  );

export const useDeleteAnnouncement = (manager: AnnouncementManager) =>
  useAnnouncementMutation((id: string) => api.deleteAnnouncement(manager, id));

export const useAnnouncementAction = (manager: AnnouncementManager) =>
  useAnnouncementMutation((vars: { id: string; action: AnnouncementAction }) =>
    api.transitionAnnouncement(manager, vars.id, vars.action),
  );
