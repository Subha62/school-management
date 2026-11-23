import Box from '@mui/material/Box';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import { useState } from 'react';
import { PageHeader } from '../../../components/PageHeader';
import type { AnnouncementManager } from '../../../types/announcements';
import { AnnouncementInbox } from './AnnouncementInbox';
import { AnnouncementManage } from './AnnouncementManage';

type TabId = 'inbox' | 'manage';

interface AnnouncementsViewProps {
  /** Undefined for students, who can only read. */
  manager?: AnnouncementManager;
}

/** The Announcements page of every role: an inbox, plus a "Manage" tab for administrators and teachers. */
export function AnnouncementsView({ manager }: AnnouncementsViewProps) {
  const [tab, setTab] = useState<TabId>('inbox');

  return (
    <>
      <PageHeader
        title="Announcements"
        subtitle={
          manager
            ? 'Read what the school has shared, and write announcements for your audience.'
            : 'What the school has shared with you.'
        }
      />
      {manager ? (
        <>
          <Tabs
            value={tab}
            onChange={(_event, value: TabId) => setTab(value)}
            aria-label="Announcement views"
            variant="scrollable"
            scrollButtons="auto"
            sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
          >
            <Tab value="inbox" label="Inbox" id="announcements-tab-inbox" sx={{ minHeight: 48 }} />
            <Tab
              value="manage"
              label="Manage"
              id="announcements-tab-manage"
              sx={{ minHeight: 48 }}
            />
          </Tabs>
          <Box role="tabpanel" aria-labelledby={`announcements-tab-${tab}`}>
            {tab === 'inbox' ? <AnnouncementInbox /> : <AnnouncementManage manager={manager} />}
          </Box>
        </>
      ) : (
        <AnnouncementInbox />
      )}
    </>
  );
}
