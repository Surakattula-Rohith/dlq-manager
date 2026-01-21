import { useQuery } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { kafkaApi } from '../api/kafka';
import { Server, Database, CheckCircle, XCircle } from 'lucide-react';

export function SettingsPage() {
  const { data: clusterInfo, isLoading: loadingCluster, error: clusterError } = useQuery({
    queryKey: ['clusterInfo'],
    queryFn: kafkaApi.getClusterInfo,
  });

  const { data: topics, isLoading: loadingTopics } = useQuery({
    queryKey: ['kafkaTopics'],
    queryFn: kafkaApi.getTopics,
  });

  const isConnected = !clusterError && clusterInfo;

  return (
    <div className="min-h-screen">
      <Header
        title="Settings"
        subtitle="Configure your DLQ Manager"
      />

      <div className="p-6">
        {/* Connection Status */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-4">Kafka Connection</h3>

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
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Controller Node */}
              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Server className="w-5 h-5 text-gray-500" />
                  <h4 className="font-medium text-gray-900">Controller Node</h4>
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-500">ID:</span>
                    <span className="font-mono">{clusterInfo.controller.id}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Host:</span>
                    <span className="font-mono">{clusterInfo.controller.host}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Port:</span>
                    <span className="font-mono">{clusterInfo.controller.port}</span>
                  </div>
                </div>
              </div>

              {/* Cluster Nodes */}
              <div className="bg-gray-50 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-5 h-5 text-gray-500" />
                  <h4 className="font-medium text-gray-900">Cluster Nodes ({clusterInfo.nodes.length})</h4>
                </div>
                <div className="space-y-2">
                  {clusterInfo.nodes.map((node) => (
                    <div key={node.id} className="flex items-center justify-between text-sm bg-white rounded px-3 py-2">
                      <span className="text-gray-700">Node {node.id}</span>
                      <span className="font-mono text-gray-500">{node.host}:{node.port}</span>
                    </div>
                  ))}
                </div>
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
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Partitions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {topics.map((topic) => (
                    <tr key={topic.name} className="hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-sm">{topic.name}</td>
                      <td className="px-4 py-3 text-gray-500">{topic.partitions}</td>
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
