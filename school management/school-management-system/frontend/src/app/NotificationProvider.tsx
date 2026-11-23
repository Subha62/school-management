import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import { useMemo, useState, type ReactNode } from 'react';
import { NotificationContext, type Notifier } from './notifications';

interface Message {
  text: string;
  severity: 'success' | 'error';
  key: number;
}

/** One snackbar at a time; a new message replaces the previous one instead of stacking. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<Message | null>(null);

  const notifier = useMemo<Notifier>(() => {
    const show = (severity: Message['severity']) => (text: string) =>
      setMessage({ text, severity, key: Date.now() });
    return { success: show('success'), error: show('error') };
  }, []);

  return (
    <NotificationContext.Provider value={notifier}>
      {children}
      <Snackbar
        key={message?.key}
        open={message !== null}
        autoHideDuration={message?.severity === 'error' ? 8000 : 4000}
        onClose={(_event, reason) => {
          if (reason !== 'clickaway') setMessage(null);
        }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={message?.severity ?? 'success'}
          variant="filled"
          onClose={() => setMessage(null)}
          sx={{ width: '100%' }}
        >
          {message?.text}
        </Alert>
      </Snackbar>
    </NotificationContext.Provider>
  );
}
