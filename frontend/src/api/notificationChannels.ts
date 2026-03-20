import apiClient from './client';
import type { NotificationChannel } from '../types';

export const notificationChannelsApi = {
  getAll: async (): Promise<NotificationChannel[]> => {
    const response = await apiClient.get('/api/notification-channels');
    return response.data.channels;
  },

  create: async (data: {
    name: string;
    type: string;
    configuration: string;
  }): Promise<NotificationChannel> => {
    const response = await apiClient.post('/api/notification-channels', data);
    return response.data.channel;
  },

  update: async (id: string, data: {
    name: string;
    type: string;
    configuration: string;
    enabled: boolean;
  }): Promise<NotificationChannel> => {
    const response = await apiClient.put(`/api/notification-channels/${id}`, data);
    return response.data.channel;
  },

  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/notification-channels/${id}`);
  },

  test: async (id: string): Promise<{ success: boolean; message?: string; error?: string }> => {
    const response = await apiClient.post(`/api/notification-channels/${id}/test`);
    return response.data;
  },
};
