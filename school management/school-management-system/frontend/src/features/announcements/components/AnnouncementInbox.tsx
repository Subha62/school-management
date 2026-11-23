import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Box from '@mui/material/Box';
import FormControlLabel from '@mui/material/FormControlLabel';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TablePagination from '@mui/material/TablePagination';
import Typography from '@mui/material/Typography';
import { useRef, useState, type ReactNode } from 'react';
import { useNotify } from '../../../app/notifications';
import { EmptyState } from '../../../components/EmptyState';
import { ErrorState } from '../../../components/ErrorState';
import { LoadingState } from '../../../components/LoadingState';
import { StatusChip } from '../../../components/StatusChip';
import type { Announcement } from '../../../types/announcements';
import { formatDateTime } from '../../assignments/labels';
import { useFeed, useMarkRead, useUnreadCount } from '../hooks';
import { audienceText } from '../labels';

const PAGE_SIZES = [10, 20, 50];

/** What the signed-in person can read now, newest first. Opening an announcement marks it read. */
export function AnnouncementInbox() {
  const notify = useNotify();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(PAGE_SIZES[0]!);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  // Kept so an item that was just read does not vanish from the "unread only" list while it is open.
  const [opened, setOpened] = useState<Announcement | null>(null);
  const feed = useFeed({ page, size, unread: unreadOnly });
  const unread = useUnreadCount();
  const markRead = useMarkRead();
  const requested = useRef(new Set<string>());

  const toggle = (announcement: Announcement, expanded: boolean) => {
    setExpandedId(expanded ? announcement.id : null);
    if (!expanded) return;
    setOpened(announcement);
    if (announcement.read || requested.current.has(announcement.id)) return;
    requested.current.add(announcement.id);
    markRead.mutate(announcement.id, {
      onError: (error) => {
        requested.current.delete(announcement.id);
        notify.error(error.message);
      },
    });
  };

  const changeFilter = (value: boolean) => {
    setUnreadOnly(value);
    setPage(0);
    setExpandedId(null);
    setOpened(null);
  };

  const rows = feed.data?.content ?? [];
  const visible =
    unreadOnly && opened && expandedId === opened.id && !rows.some((row) => row.id === opened.id)
      ? [{ ...opened, read: true }, ...rows]
      : rows;

  let content: ReactNode;
  if (feed.error && !feed.data) {
    content = <ErrorState error={feed.error} onRetry={() => void feed.refetch()} />;
  } else if (feed.isPending) {
    content = <LoadingState label="Loading announcements" />;
  } else if (visible.length === 0) {
    content = (
      <Paper variant="outlined">
        <EmptyState
          title={unreadOnly ? 'No unread announcements' : 'No announcements'}
          hint={
            unreadOnly
              ? 'You have read everything that is current.'
              : 'Announcements for you appear here.'
          }
        />
      </Paper>
    );
  } else {
    content = (
      <Stack component="ul" spacing={1.5} sx={{ listStyle: 'none', m: 0, p: 0 }}>
        {visible.map((announcement) => (
          <Accordion
            key={announcement.id}
            component="li"
            variant="outlined"
            disableGutters
            expanded={expandedId === announcement.id}
            onChange={(_event, expanded) => toggle(announcement, expanded)}
            slotProps={{ heading: { component: 'h3' } }}
            sx={{ '&::before': { display: 'none' } }}
          >
            <AccordionSummary
              expandIcon={<ExpandMoreIcon />}
              sx={{ minHeight: 56, '& .MuiAccordionSummary-content': { minWidth: 0 } }}
            >
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  {announcement.read ? null : (
                    <Box
                      aria-hidden
                      sx={{
                        width: 10,
                        height: 10,
                        borderRadius: '50%',
                        bgcolor: 'primary.main',
                        flexShrink: 0,
                      }}
                    />
                  )}
                  <Typography
                    component="span"
                    sx={{
                      fontWeight: announcement.read ? 400 : 700,
                      overflowWrap: 'anywhere',
                    }}
                  >
                    {announcement.title}
                  </Typography>
                </Stack>
                <Stack
                  direction="row"
                  spacing={1}
                  useFlexGap
                  sx={{ mt: 0.5, flexWrap: 'wrap', alignItems: 'center' }}
                >
                  <StatusChip label={audienceText(announcement)} />
                  {announcement.read ? null : <StatusChip label="Unread" tone="info" />}
                  <Typography variant="body2" color="text.secondary" component="span">
                    {announcement.authorName} ·{' '}
                    {formatDateTime(
                      announcement.publishAt ?? announcement.publishedAt ?? announcement.createdAt,
                    )}
                  </Typography>
                </Stack>
              </Box>
            </AccordionSummary>
            <AccordionDetails>
              <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {announcement.body}
              </Typography>
              {announcement.expiresAt ? (
                <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 2 }}>
                  Visible until {formatDateTime(announcement.expiresAt)}
                </Typography>
              ) : null}
            </AccordionDetails>
          </Accordion>
        ))}
      </Stack>
    );
  }

  return (
    <Box>
      <Stack
        direction="row"
        spacing={2}
        useFlexGap
        sx={{ mb: 2, flexWrap: 'wrap', alignItems: 'center' }}
      >
        <FormControlLabel
          label="Unread only"
          control={
            <Switch checked={unreadOnly} onChange={(event) => changeFilter(event.target.checked)} />
          }
        />
        {unread.data ? (
          <Typography variant="body2" color="text.secondary" role="status">
            {unread.data.count} unread
          </Typography>
        ) : null}
      </Stack>
      {content}
      {feed.data && feed.data.totalElements > 0 ? (
        <TablePagination
          component="div"
          count={feed.data.totalElements}
          page={feed.data.page}
          rowsPerPage={feed.data.size}
          rowsPerPageOptions={PAGE_SIZES}
          onPageChange={(_event, next) => {
            setPage(next);
            setExpandedId(null);
          }}
          onRowsPerPageChange={(event) => {
            setSize(Number(event.target.value));
            setPage(0);
          }}
          labelRowsPerPage="Rows"
          sx={{
            '.MuiTablePagination-toolbar': { flexWrap: 'wrap', justifyContent: 'center', px: 1 },
          }}
        />
      ) : null}
    </Box>
  );
}
