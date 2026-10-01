import apiClient from './client';
import type { ActivityAction, ActivityPage } from '../types';

export const activityApi = {
  // Newest first; username and action are optional filters
  getActivity: async (page: number, size: number, username?: string, action?: ActivityAction): Promise<ActivityPage> => {
    const response = await apiClient.get('/api/activity', {
      params: { page, size, username: username || undefined, action: action || undefined },
    });
    return response.data;
  },
};
