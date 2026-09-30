import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryCache, QueryClient, QueryClientProvider, MutationCache } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Layout } from './components/layout';
import { AuthGate } from './components/auth/AuthGate';
import {
  DashboardPage,
  DlqTopicsPage,
  DlqTopicDetailPage,
  ReplayHistoryPage,
  AlertsPage,
  SettingsPage,
} from './pages';
import { ThemeContext } from './context/ThemeContext';
import { AUTH_SESSION_KEY } from './context/AuthContext';
import { useDarkMode } from './hooks/useDarkMode';

const isUnauthorized = (error: unknown) =>
  isAxiosError(error) && error.response?.status === 401 && !error.config?.url?.startsWith('/api/auth/');

// A 401 from any API call means the session ended (expired or signed out elsewhere):
// re-check who is signed in, which brings back the login page
const handleApiError = (error: unknown) => {
  if (isUnauthorized(error)) {
    queryClient.invalidateQueries({ queryKey: AUTH_SESSION_KEY });
  }
};

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleApiError }),
  mutationCache: new MutationCache({ onError: handleApiError }),
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => !isUnauthorized(error) && failureCount < 1,
      staleTime: 30000, // 30 seconds
    },
  },
});

function App() {
  const { isDark, toggle } = useDarkMode();

  return (
    <ThemeContext.Provider value={{ isDark, toggleDark: toggle }}>
    <QueryClientProvider client={queryClient}>
      <AuthGate>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<DashboardPage />} />
              <Route path="dlq-topics" element={<DlqTopicsPage />} />
              <Route path="dlq-topics/:id" element={<DlqTopicDetailPage />} />
              <Route path="replay-history" element={<ReplayHistoryPage />} />
              <Route path="alerts" element={<AlertsPage />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthGate>
    </QueryClientProvider>
    </ThemeContext.Provider>
  );
}

export default App;
