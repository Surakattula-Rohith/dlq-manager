import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { Database, Loader2, LogIn } from 'lucide-react';
import { authApi } from '../api/auth';
import { AUTH_SESSION_KEY } from '../context/AuthContext';

const DEMO_ACCOUNTS = ['admin', 'operator', 'viewer'];

interface LoginPageProps {
  demoAccounts: boolean;
}

export function LoginPage({ demoAccounts }: LoginPageProps) {
  const queryClient = useQueryClient();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const loginMutation = useMutation({
    mutationFn: () => authApi.login(username.trim(), password),
    onSuccess: (session) => queryClient.setQueryData(AUTH_SESSION_KEY, session),
  });

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    loginMutation.mutate();
  };

  const fillDemoAccount = (name: string) => {
    setUsername(name);
    setPassword(name);
    loginMutation.reset();
  };

  const errorMessage = !loginMutation.isError
    ? null
    : isAxiosError(loginMutation.error) && loginMutation.error.response?.status === 401
      ? 'Wrong username or password'
      : 'Could not sign in. Please try again.';

  const inputClass = "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-orange-500";

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm">
        <div className="flex items-center justify-center gap-2 mb-6">
          <Database className="w-9 h-9 text-orange-500" />
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">DLQ Manager</h1>
            <p className="text-xs text-gray-500 dark:text-gray-400">Kafka Dead Letter Queue</p>
          </div>
        </div>

        <form
          onSubmit={handleSubmit}
          className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 space-y-4"
        >
          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Username
            </label>
            <input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
              className={inputClass}
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              className={inputClass}
            />
          </div>

          {errorMessage && (
            <p className="text-sm text-red-600 dark:text-red-400">{errorMessage}</p>
          )}

          <button
            type="submit"
            disabled={loginMutation.isPending}
            className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors disabled:opacity-50"
          >
            {loginMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <LogIn className="w-4 h-4" />}
            Sign in
          </button>
        </form>

        {demoAccounts && (
          <div className="mt-4 p-4 rounded-lg border border-dashed border-gray-300 dark:border-gray-600 text-sm">
            <p className="text-gray-600 dark:text-gray-400 mb-2">Demo accounts (password = username):</p>
            <div className="flex gap-2">
              {DEMO_ACCOUNTS.map((name) => (
                <button
                  key={name}
                  type="button"
                  onClick={() => fillDemoAccount(name)}
                  className="px-3 py-1 rounded-md bg-gray-200 dark:bg-gray-700 text-gray-800 dark:text-gray-200 hover:bg-orange-100 dark:hover:bg-gray-600 transition-colors"
                >
                  {name}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
