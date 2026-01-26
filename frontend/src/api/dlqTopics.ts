import apiClient from './client';
import type { DlqTopic, MessagePage, ErrorBreakdown } from '../types';

export const dlqTopicsApi = {
  // Get all DLQ topics
  getAll: async (): Promise<DlqTopic[]> => {
    const response = await apiClient.get('/api/dlq-topics');
    return response.data.dlqTopics;
  },

  // Get single DLQ topic by ID
  getById: async (id: string): Promise<DlqTopic> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}`);
    return response.data.dlqTopic;
  },

  // Create new DLQ topic
  create: async (data: Partial<DlqTopic>): Promise<DlqTopic> => {
    const response = await apiClient.post('/api/dlq-topics', data);
    return response.data.dlqTopic;
  },

  // Update DLQ topic
  update: async (id: string, data: Partial<DlqTopic>): Promise<DlqTopic> => {
    const response = await apiClient.put(`/api/dlq-topics/${id}`, data);
    return response.data.dlqTopic;
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
    // Transform backend response to match frontend types
    const data = response.data;
    return {
      messages: data.messages.map((msg: Record<string, unknown>) => ({
        ...msg,
        key: msg.messageKey as string | undefined,
        // Convert payload object to string for display
        payload: typeof msg.payload === 'object' ? JSON.stringify(msg.payload) : msg.payload,
      })),
      currentPage: data.pagination.currentPage,
      totalPages: data.pagination.totalPages,
      totalMessages: data.pagination.totalMessages,
      pageSize: data.pagination.pageSize,
    };
  },

  // Get message count
  getMessageCount: async (id: string): Promise<{ count: number }> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/message-count`);
    // Backend returns { totalMessages: N }, frontend expects { count: N }
    return { count: response.data.totalMessages };
  },

  // Get error breakdown
  getErrorBreakdown: async (id: string): Promise<ErrorBreakdown> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/error-breakdown`);
    const data = response.data;
    return {
      ...data,
      uniqueErrorTypes: data.errorBreakdown?.length || 0,
    };
  },
};
