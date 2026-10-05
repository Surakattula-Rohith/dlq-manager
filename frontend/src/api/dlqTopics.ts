import apiClient from './client';
import type { DlqTopic, MessagePage, ErrorBreakdown, MessageFilters, DlqTrend, TrendRangeCode, SourceConsumers } from '../types';

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

  // Get messages from DLQ topic (optionally filtered)
  getMessages: async (id: string, page: number = 1, size: number = 10, filters: MessageFilters = {}): Promise<MessagePage> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/messages`, {
      params: {
        page,
        size,
        search: filters.search || undefined,
        errorType: filters.errorType || undefined,
        pendingOnly: filters.pendingOnly || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
      },
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
      pendingMessages: data.pagination.pendingMessages ?? data.pagination.totalMessages,
      replayedMessages: data.pagination.replayedMessages ?? 0,
      matchingMessages: data.pagination.matchingMessages ?? data.pagination.totalMessages,
      filtered: data.pagination.filtered ?? false,
      scanLimitReached: data.pagination.scanLimitReached ?? false,
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

  // How the DLQ developed over time, in points lined up with this browser's hours
  getTrend: async (id: string, range: TrendRangeCode): Promise<DlqTrend> => {
    const utcOffsetMinutes = -new Date().getTimezoneOffset();
    const response = await apiClient.get(`/api/dlq-topics/${id}/trend`, { params: { range, utcOffsetMinutes } });
    return response.data;
  },

  // Who reads the source topic replays go to, and whether they keep up
  getSourceConsumers: async (id: string): Promise<SourceConsumers> => {
    const response = await apiClient.get(`/api/dlq-topics/${id}/source-consumers`);
    return response.data;
  },

  // Download link for the messages matching the filters (the browser handles the download)
  exportUrl: (id: string, format: 'csv' | 'json', filters: MessageFilters = {}): string => {
    const params = new URLSearchParams({ format });
    if (filters.search) params.set('search', filters.search);
    if (filters.errorType) params.set('errorType', filters.errorType);
    if (filters.pendingOnly) params.set('pendingOnly', 'true');
    if (filters.from) params.set('from', filters.from);
    if (filters.to) params.set('to', filters.to);
    return `${apiClient.defaults.baseURL ?? ''}/api/dlq-topics/${id}/messages/export?${params.toString()}`;
  },
};
