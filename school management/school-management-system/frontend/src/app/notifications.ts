import { createContext, useContext } from 'react';

export interface Notifier {
  success: (message: string) => void;
  error: (message: string) => void;
}

export const NotificationContext = createContext<Notifier | null>(null);

export function useNotify(): Notifier {
  const notifier = useContext(NotificationContext);
  if (!notifier) {
    throw new Error('useNotify must be used inside a NotificationProvider');
  }
  return notifier;
}
