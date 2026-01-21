import apiClient from './client';
import type { DlqTopic, MessagePage, ErrorBreakdown } from '../types';

export const dlqTopicsApi = {
  // Get all DLQ topics
  getAll: async (): Promise<DlqTopic[]> => {
    const response = await apiClient.get('/api/dlq-topics');
    return response.data;
  },

  // Get single DLQ topic by ID
  getById: async (id: string): Promise<DlqTopic> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}`);
    return response.data;
  },

  // Create new DLQ topic
  create: async (data: Partial<DlqTopic>): Promise<DlqTopic> => {
    const response = await apiClient.post('/api/dlq-topics', data);
    return response.data;
  },

  // Update DLQ topic
  update: async (id: string, data: Partial<DlqTopic>): Promise<DlqTopic> => {
    const response = await apiClient.put(`/api/dlq-topics/${id}`, data);
    return response.data;
  },

  // Delete DLQ topic
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/dlq-topics/${id}`);
  },

  // Get messages from DLQ topic
  getMessages: async (id: string, page: number = 1, size: number = 10): Promise<MessagePage> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/messages`, {
      params: { page, size },
    });
    return response.data;
  },

  // Get message count
  getMessageCount: async (id: string): Promise<{ count: number }> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/message-count`);
    return response.data;
  },

  // Get error breakdown
  getErrorBreakdown: async (id: string): Promise<ErrorBreakdown> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/error-breakdown`);
    return response.data;
  },
};
