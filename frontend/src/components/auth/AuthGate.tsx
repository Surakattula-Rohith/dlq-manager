import type { ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, ServerCrash } from 'lucide-react';
import { authApi } from '../../api/auth';
import { AuthContext, AUTH_SESSION_KEY } from '../../context/AuthContext';
import { LoginPage } from '../../pages/LoginPage';

/**
 * Shows the login page until someone is signed in, then the app.
 * When the session ends (logout or expiry), everything the previous user loaded is dropped.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data: session, isLoading, isError, refetch } = useQuery({
    queryKey: AUTH_SESSION_KEY,
    queryFn: authApi.me,
    staleTime: Infinity,
  });

  const logoutMutation = useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== AUTH_SESSION_KEY[0] });
      // Asking the server again also hands out a fresh CSRF cookie for the next login
      queryClient.invalidateQueries({ queryKey: AUTH_SESSION_KEY });
    },
  });

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100 dark:bg-gray-900">
        <Loader2 className="w-8 h-8 text-orange-500 animate-spin" />
      </div>
    );
  }

  if (isError || !session) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-4 bg-gray-100 dark:bg-gray-900">
        <ServerCrash className="w-10 h-10 text-gray-400" />
        <p className="text-gray-600 dark:text-gray-300">Can't reach the DLQ Manager server.</p>
        <button
          onClick={() => refetch()}
          className="px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors"
        >
          Try again
        </button>
      </div>
    );
  }

  if (!session.authenticated) {
    return <LoginPage demoAccounts={session.demoAccounts} />;
  }

  return (
    <AuthContext.Provider value={{ session, logout: () => logoutMutation.mutate() }}>
      {children}
    </AuthContext.Provider>
  );
}
