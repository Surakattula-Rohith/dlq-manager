import apiClient from './client';
import type { AuthSession } from '../types';

export const authApi = {
  // Who is signed in (always succeeds; check `authenticated`)
  me: async (): Promise<AuthSession> => {
    const response = await apiClient.get('/api/auth/me');
    return response.data;
  },

  // Spring Security's login endpoint expects form fields, not JSON
  login: async (username: string, password: string): Promise<AuthSession> => {
    const response = await apiClient.post('/api/auth/login', new URLSearchParams({ username, password }), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    return response.data;
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/api/auth/logout');
  },
};
