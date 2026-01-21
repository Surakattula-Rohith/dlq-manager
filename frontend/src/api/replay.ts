import apiClient from './client';
import type { ReplayJob, ReplayRequest, BulkReplayRequest } from '../types';

export const replayApi = {
  // Replay single message
  replaySingle: async (request: ReplayRequest): Promise<{ success: boolean; message: string; replayJob: ReplayJob }> => {
    const response = await apiClient.post('/api/replay/single', request);
    return response.data;
  },

  // Replay bulk messages
  replayBulk: async (request: BulkReplayRequest): Promise<{ success: boolean; message: string; replayJob: ReplayJob }> => {
    const response = await apiClient.post('/api/replay/bulk', request);
    return response.data;
  },

  // Get replay job status
  getJob: async (jobId: string): Promise<ReplayJob> => {
    const response = await apiClient.get(`/api/replay/jobs/${jobId}`);
    return response.data;
  },

  // Get replay history
  getHistory: async (): Promise<ReplayJob[]> => {
    const response = await apiClient.get('/api/replay/history');
    return response.data;
  },
};
