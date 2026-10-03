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

// How the app signs in to Kafka
export type KafkaAuthentication = 'NONE' | 'PLAIN' | 'SCRAM_SHA_256' | 'SCRAM_SHA_512';

export interface KafkaConfigResponse {
  success: boolean;
  bootstrapServers: string;
  configured: boolean;
  authentication: KafkaAuthentication;
  encrypted: boolean;
  username: string | null;
  // The password itself is never sent to the browser, only whether one is saved
  passwordSet: boolean;
  caCertificate: string | null;
  // false when the server has no DLQ_SECRET_KEY, so it can't store a Kafka password
  canStorePassword: boolean;
}

// What the Settings page sends to test or save a connection
export interface KafkaConnectionSettings {
  bootstrapServers: string;
  authentication: KafkaAuthentication;
  encrypted: boolean;
  username?: string;
  // Left out = keep the saved password (the server only does that for the same brokers and username)
  password?: string;
  caCertificate?: string;
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
  saveConfig: async (settings: KafkaConnectionSettings): Promise<KafkaConfigResponse> => {
    const response = await apiClient.put('/api/kafka/config', settings);
    return response.data;
  },

  // Test Kafka connection
  testConnection: async (settings: KafkaConnectionSettings): Promise<ConnectionTestResult> => {
    const response = await apiClient.post('/api/kafka/config/test', settings);
    return response.data;
  },
};
