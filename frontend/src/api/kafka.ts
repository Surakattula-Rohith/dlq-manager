import apiClient from './client';

export interface KafkaTopic {
  name: string;
}

export interface ClusterInfo {
  clusterId: string;
  topicCount: number;
  brokerCount: number;
}

export interface DiscoveredDlq {
  dlqTopic: string;
  sourceTopic: string;
}

export interface KafkaConfigResponse {
  success: boolean;
  bootstrapServers: string;
  configured: boolean;
}

export interface ConnectionTestResult {
  success: boolean;
  clusterId?: string;
  brokerCount?: number;
  error?: string;
}

export const kafkaApi = {
  // Get all Kafka topics
  getTopics: async (): Promise<KafkaTopic[]> => {
    const response = await apiClient.get('/api/kafka/topics');
    // Backend returns { topics: ["name1", "name2"] }
    return response.data.topics.map((name: string) => ({ name }));
  },

  // Get cluster info
  getClusterInfo: async (): Promise<ClusterInfo> => {
    const response = await apiClient.get('/api/kafka/cluster-info');
    return response.data.cluster;
  },

  // Auto-discover DLQ topics
  discoverDlqs: async (): Promise<DiscoveredDlq[]> => {
    const response = await apiClient.get('/api/kafka/discover-dlqs');
    // Backend returns { dlqMappings: { "dlq-topic": "source-topic" } }
    // Transform to array of { dlqTopic, sourceTopic }
    const mappings = response.data.dlqMappings || {};
    return Object.entries(mappings).map(([dlqTopic, sourceTopic]) => ({
      dlqTopic,
      sourceTopic: sourceTopic as string,
    }));
  },

  // Get Kafka config
  getConfig: async (): Promise<KafkaConfigResponse> => {
    const response = await apiClient.get('/api/kafka/config');
    return response.data;
  },

  // Save Kafka config
  saveConfig: async (bootstrapServers: string): Promise<KafkaConfigResponse> => {
    const response = await apiClient.put('/api/kafka/config', { bootstrapServers });
    return response.data;
  },

  // Test Kafka connection
  testConnection: async (bootstrapServers: string): Promise<ConnectionTestResult> => {
    const response = await apiClient.post('/api/kafka/config/test', { bootstrapServers });
    return response.data;
  },
};
