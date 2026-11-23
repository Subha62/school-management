import { BrowserRouter } from 'react-router-dom';
import { AppProviders } from './app/AppProviders';
import { AuthProvider } from './auth/AuthProvider';
import { AppRoutes } from './routes/AppRoutes';

export default function App() {
  return (
    <AppProviders>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </AppProviders>
  );
}
