import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { kafkaApi } from '../api/kafka';
import type { ConnectionTestResult } from '../api/kafka';
import { Server, Database, CheckCircle, XCircle, Loader2, Plug, Save } from 'lucide-react';

export function SettingsPage() {
  const queryClient = useQueryClient();

  const { data: kafkaConfig, isLoading: loadingConfig } = useQuery({
    queryKey: ['kafkaConfig'],
    queryFn: kafkaApi.getConfig,
  });

  const { data: clusterInfo, isLoading: loadingCluster, error: clusterError } = useQuery({
    queryKey: ['clusterInfo'],
    queryFn: kafkaApi.getClusterInfo,
    enabled: kafkaConfig?.configured !== false,
    retry: false,
  });

  const { data: topics, isLoading: loadingTopics } = useQuery({
    queryKey: ['kafkaTopics'],
    queryFn: kafkaApi.getTopics,
    enabled: kafkaConfig?.configured !== false,
    retry: false,
  });

  const [bootstrapServers, setBootstrapServers] = useState('');
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    if (kafkaConfig?.bootstrapServers) {
      setBootstrapServers(kafkaConfig.bootstrapServers);
    }
  }, [kafkaConfig]);

  const testMutation = useMutation({
    mutationFn: (servers: string) => kafkaApi.testConnection(servers),
    onSuccess: (data) => setTestResult(data),
  });

  const saveMutation = useMutation({
    mutationFn: (servers: string) => kafkaApi.saveConfig(servers),
    onSuccess: () => {
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
      queryClient.invalidateQueries({ queryKey: ['kafkaConfig'] });
      queryClient.invalidateQueries({ queryKey: ['clusterInfo'] });
      queryClient.invalidateQueries({ queryKey: ['kafkaTopics'] });
    },
  });

  const isConnected = !clusterError && clusterInfo;
  const isFirstTime = kafkaConfig && !kafkaConfig.configured;

  if (loadingConfig) {
    return (
      <div className="min-h-screen">
        <Header title="Settings" subtitle="Configure your DLQ Manager" />
        <div className="p-6 text-gray-500">Loading configuration...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <Header
        title="Settings"
        subtitle="Configure your DLQ Manager"
      />

      <div className="p-6">
        {/* First-time setup banner */}
        {isFirstTime && (
          <div className="bg-orange-50 border border-orange-200 rounded-lg p-4 mb-6">
            <h3 className="font-semibold text-orange-800">Welcome to DLQ Manager</h3>
            <p className="text-sm text-orange-700 mt-1">
              To get started, enter your Kafka bootstrap servers below and test the connection.
            </p>
          </div>
        )}

        {/* Kafka Configuration */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Kafka Configuration</h3>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Bootstrap Servers
              </label>
              <input
                type="text"
                value={bootstrapServers}
                onChange={(e) => {
                  setBootstrapServers(e.target.value);
                  setTestResult(null);
                  setSaveSuccess(false);
                }}
                placeholder="localhost:9092"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
              />
              <p className="text-xs text-gray-500 mt-1">
                Comma-separated list of Kafka broker addresses (e.g., broker1:9092,broker2:9092)
              </p>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => {
                  setTestResult(null);
                  testMutation.mutate(bootstrapServers);
                }}
                disabled={!bootstrapServers.trim() || testMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {testMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Plug className="w-4 h-4" />
                )}
                Test Connection
              </button>

              <button
                onClick={() => saveMutation.mutate(bootstrapServers)}
                disabled={!bootstrapServers.trim() || saveMutation.isPending || !testResult?.success}
                title={!testResult?.success ? 'Test the connection first before saving' : ''}
                className="flex items-center gap-2 px-4 py-2 bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {saveMutation.isPending ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Save className="w-4 h-4" />
                )}
                Save
              </button>
            </div>

            {/* Test result feedback */}
            {testResult && (
              <div className={`rounded-lg p-3 ${testResult.success ? 'bg-green-50 border border-green-200' : 'bg-red-50 border border-red-200'}`}>
                <div className="flex items-center gap-2">
                  {testResult.success ? (
                    <CheckCircle className="w-5 h-5 text-green-600" />
                  ) : (
                    <XCircle className="w-5 h-5 text-red-600" />
                  )}
                  <span className={`font-medium ${testResult.success ? 'text-green-700' : 'text-red-700'}`}>
                    {testResult.success ? 'Connection successful' : 'Connection failed'}
                  </span>
                </div>
                {testResult.success && (
                  <p className="text-sm text-green-600 mt-1">
                    Cluster: {testResult.clusterId} | Brokers: {testResult.brokerCount}
                  </p>
                )}
                {!testResult.success && testResult.error && (
                  <p className="text-sm text-red-600 mt-1">{testResult.error}</p>
                )}
              </div>
            )}

            {/* Save success feedback */}
            {saveSuccess && (
              <div className="bg-green-50 border border-green-200 rounded-lg p-3">
                <div className="flex items-center gap-2">
                  <CheckCircle className="w-5 h-5 text-green-600" />
                  <span className="font-medium text-green-700">Configuration saved successfully</span>
                </div>
              </div>
            )}

            {/* Save error feedback */}
            {saveMutation.isError && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-3">
                <div className="flex items-center gap-2">
                  <XCircle className="w-5 h-5 text-red-600" />
                  <span className="font-medium text-red-700">Failed to save configuration</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Connection Status */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Connection Status</h3>

          <div className="flex items-center gap-4 mb-6">
            <div className={`p-3 rounded-lg ${isConnected ? 'bg-green-100' : 'bg-red-100'}`}>
              {isConnected ? (
                <CheckCircle className="w-6 h-6 text-green-600" />
              ) : (
                <XCircle className="w-6 h-6 text-red-600" />
              )}
            </div>
            <div>
              <p className={`font-medium ${isConnected ? 'text-green-700' : 'text-red-700'}`}>
                {isConnected ? 'Connected to Kafka' : 'Connection Failed'}
              </p>
              <p className="text-sm text-gray-500">
                {isConnected
                  ? `Cluster ID: ${clusterInfo?.clusterId || 'N/A'}`
                  : 'Unable to connect to Kafka cluster'}
              </p>
            </div>
          </div>

          {loadingCluster ? (
            <div className="text-gray-500">Loading cluster info...</div>
          ) : isConnected && clusterInfo ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Server className="w-5 h-5 text-gray-500" />
                  <h4 className="font-medium text-gray-900">Cluster ID</h4>
                </div>
                <p className="font-mono text-sm text-gray-700 break-all">{clusterInfo.clusterId}</p>
              </div>

              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-5 h-5 text-gray-500" />
                  <h4 className="font-medium text-gray-900">Brokers</h4>
                </div>
                <p className="text-2xl font-bold text-gray-900">{clusterInfo.brokerCount}</p>
              </div>

              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-5 h-5 text-gray-500" />
                  <h4 className="font-medium text-gray-900">Topics</h4>
                </div>
                <p className="text-2xl font-bold text-gray-900">{clusterInfo.topicCount}</p>
              </div>
            </div>
          ) : (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
              <p className="font-medium">Failed to connect to Kafka</p>
              <p className="text-sm mt-1">
                Please ensure your Kafka cluster is running and accessible at the configured bootstrap servers.
              </p>
            </div>
          )}
        </div>

        {/* Topics Overview */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Kafka Topics</h3>

          {loadingTopics ? (
            <div className="text-gray-500">Loading topics...</div>
          ) : topics && topics.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Topic Name</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {topics.map((topic) => (
                    <tr key={topic.name} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-sm">{topic.name}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-gray-500 text-center py-8">
              No topics found in the Kafka cluster.
            </div>
          )}
        </div>

        {/* API Configuration */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">API Configuration</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Backend API URL</label>
              <input
                type="text"
                value={import.meta.env.VITE_API_URL || 'http://localhost:8080'}
                readOnly
                className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-600"
              />
              <p className="text-xs text-gray-500 mt-1">
                Set via VITE_API_URL environment variable
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
