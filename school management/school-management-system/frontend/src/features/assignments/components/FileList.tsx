import DownloadIcon from '@mui/icons-material/Download';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { toApiError } from '../../../api/errors';
import { useNotify } from '../../../app/notifications';
import type { Attachment } from '../../../types/assignments';
import { downloadFile } from '../api';
import { formatBytes } from '../labels';

interface FileListProps {
  attachments: Attachment[];
  /** The API path that downloads the given attachment. */
  pathFor: (attachmentId: string) => string;
  /** When given, each file also gets a Remove button. */
  onRemove?: (attachment: Attachment) => void;
  removing?: boolean;
  label: string;
}

/** Downloads go through the API client, so a failure shows a message instead of a raw error page. */
export function FileList({ attachments, pathFor, onRemove, removing, label }: FileListProps) {
  const notify = useNotify();
  const [busy, setBusy] = useState<string | null>(null);

  if (attachments.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No files.
      </Typography>
    );
  }

  const download = async (attachment: Attachment) => {
    setBusy(attachment.id);
    try {
      await downloadFile(pathFor(attachment.id), attachment.name);
    } catch (cause) {
      notify.error(toApiError(cause).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Stack component="ul" spacing={1} aria-label={label} sx={{ m: 0, p: 0, listStyle: 'none' }}>
      {attachments.map((attachment) => (
        <Stack
          component="li"
          key={attachment.id}
          direction="row"
          spacing={1}
          sx={{ alignItems: 'center', justifyContent: 'space-between' }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ overflowWrap: 'anywhere' }}>{attachment.name}</Typography>
            <Typography variant="caption" color="text.secondary">
              {formatBytes(attachment.sizeBytes)}
            </Typography>
          </Box>
          <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0 }}>
            <Button
              size="small"
              startIcon={<DownloadIcon />}
              loading={busy === attachment.id}
              aria-label={`Download ${attachment.name}`}
              onClick={() => void download(attachment)}
            >
              Download
            </Button>
            {onRemove ? (
              <Button
                size="small"
                color="error"
                disabled={removing}
                aria-label={`Remove ${attachment.name}`}
                onClick={() => onRemove(attachment)}
              >
                Remove
              </Button>
            ) : null}
          </Stack>
        </Stack>
      ))}
    </Stack>
  );
}
