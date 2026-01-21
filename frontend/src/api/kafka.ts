import apiClient from './client';

export interface KafkaTopic {
  name: string;
  partitions: number;
}

export interface ClusterInfo {
  clusterId: string;
  nodes: { id: number; host: string; port: number }[];
  controller: { id: number; host: string; port: number };
}

export interface DiscoveredDlq {
  dlqTopic: string;
  sourceTopic: string;
}

export const kafkaApi = {
  // Get all Kafka topics
  getTopics: async (): Promise<KafkaTopic[]> => {
    const response = await apiClient.get('/api/kafka/topics');
    return response.data;
  },

  // Get cluster info
  getClusterInfo: async (): Promise<ClusterInfo> => {
    const response = await apiClient.get('/api/kafka/cluster-info');
    return response.data;
  },

  // Auto-discover DLQ topics
  discoverDlqs: async (): Promise<DiscoveredDlq[]> => {
    const response = await apiClient.get('/api/kafka/discover-dlqs');
    return response.data;
  },
};
