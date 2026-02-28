import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './components/layout';
import {
  DashboardPage,
  DlqTopicsPage,
  DlqTopicDetailPage,
  ReplayHistoryPage,
  AlertsPage,
  SettingsPage,
} from './pages';
import { ThemeContext } from './context/ThemeContext';
import { useDarkMode } from './hooks/useDarkMode';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30000, // 30 seconds
    },
  },
});

function App() {
  const { isDark, toggle } = useDarkMode();

  return (
    <ThemeContext.Provider value={{ isDark, toggleDark: toggle }}>
    <QueryClientProvider client={queryClient}>
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
    </QueryClientProvider>
    </ThemeContext.Provider>
  );
}

export default App;
