import apiClient from './client';
import type { AlertRule, AlertEvent } from '../types';

export const alertsApi = {
  // --- Alert Rules ---

  getRules: async (): Promise<AlertRule[]> => {
    const response = await apiClient.get('/api/alert-rules');
    return response.data.alertRules;
  },

  createRule: async (data: {
    name: string;
    dlqTopicId: string;
    alertType: string;
    threshold: number;
    windowMinutes?: number;
    notificationChannelId?: string;
    cooldownMinutes: number;
  }): Promise<AlertRule> => {
    const response = await apiClient.post('/api/alert-rules', data);
    return response.data.alertRule;
  },

  updateRule: async (id: string, data: {
    name: string;
    alertType: string;
    threshold: number;
    windowMinutes?: number;
    notificationChannelId?: string;
    cooldownMinutes: number;
    enabled: boolean;
  }): Promise<AlertRule> => {
    const response = await apiClient.put(`/api/alert-rules/${id}`, data);
    return response.data.alertRule;
  },

  toggleRule: async (id: string): Promise<AlertRule> => {
    const response = await apiClient.patch(`/api/alert-rules/${id}/toggle`);
    return response.data.alertRule;
  },

  deleteRule: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/alert-rules/${id}`);
  },

  // --- Alert Events ---

  getEvents: async (): Promise<{ alertEvents: AlertEvent[]; firingCount: number }> => {
    const response = await apiClient.get('/api/alert-events');
    return { alertEvents: response.data.alertEvents, firingCount: response.data.firingCount };
  },

  acknowledgeEvent: async (id: string): Promise<AlertEvent> => {
    const response = await apiClient.post(`/api/alert-events/${id}/acknowledge`);
    return response.data.alertEvent;
  },

  snoozeEvent: async (id: string, minutes: number): Promise<AlertEvent> => {
    const response = await apiClient.post(`/api/alert-events/${id}/snooze`, { minutes });
    return response.data.alertEvent;
  },
};
