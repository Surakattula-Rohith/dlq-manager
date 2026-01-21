import { useQuery } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { replayApi } from '../api/replay';
import { CheckCircle, XCircle, Clock, AlertTriangle } from 'lucide-react';

export function ReplayHistoryPage() {
  const { data: replayJobs, isLoading, refetch } = useQuery({
    queryKey: ['replayHistory'],
    queryFn: replayApi.getHistory,
  });

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle className="w-5 h-5 text-green-500" />;
      case 'FAILED':
        return <XCircle className="w-5 h-5 text-red-500" />;
      case 'IN_PROGRESS':
        return <Clock className="w-5 h-5 text-blue-500 animate-pulse" />;
      case 'PARTIALLY_COMPLETED':
        return <AlertTriangle className="w-5 h-5 text-yellow-500" />;
      default:
        return <Clock className="w-5 h-5 text-gray-500" />;
    }
  };

  const getStatusStyle = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-700';
      case 'FAILED':
        return 'bg-red-100 text-red-700';
      case 'IN_PROGRESS':
        return 'bg-blue-100 text-blue-700';
      case 'PARTIALLY_COMPLETED':
        return 'bg-yellow-100 text-yellow-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  return (
    <div className="min-h-screen">
      <Header
        title="Replay History"
        subtitle="Track all message replay operations"
        onRefresh={() => refetch()}
        isRefreshing={isLoading}
      />

      <div className="p-6">
        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
          <StatCard
            label="Total Jobs"
            value={replayJobs?.length || 0}
            color="text-gray-900"
          />
          <StatCard
            label="Completed"
            value={replayJobs?.filter(j => j.status === 'COMPLETED').length || 0}
            color="text-green-600"
          />
          <StatCard
            label="Failed"
            value={replayJobs?.filter(j => j.status === 'FAILED').length || 0}
            color="text-red-600"
          />
          <StatCard
            label="In Progress"
            value={replayJobs?.filter(j => j.status === 'IN_PROGRESS').length || 0}
            color="text-blue-600"
          />
        </div>

        {/* Jobs Table */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500">Loading...</div>
            ) : replayJobs && replayJobs.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Job ID</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">DLQ Topic</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Messages</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Success Rate</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Initiated By</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {replayJobs.map((job) => (
                    <tr key={job.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          {getStatusIcon(job.status)}
                          <span className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${getStatusStyle(job.status)}`}>
                            {job.status.replace('_', ' ')}
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="font-mono text-sm text-gray-900">{job.id.slice(0, 8)}...</span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="text-gray-900 font-medium">{job.dlqTopicName || '-'}</span>
                        {job.sourceTopic && (
                          <span className="text-gray-500 text-sm ml-2">→ {job.sourceTopic}</span>
                        )}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="text-sm">
                          <span className="text-green-600 font-medium">{job.succeeded}</span>
                          <span className="text-gray-400"> / </span>
                          <span className="text-gray-900">{job.totalMessages}</span>
                          {job.failed > 0 && (
                            <span className="text-red-600 ml-2">({job.failed} failed)</span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <div className="w-20 bg-gray-200 rounded-full h-2">
                            <div
                              className={`h-2 rounded-full ${
                                (job.successRate || 0) === 100
                                  ? 'bg-green-500'
                                  : (job.successRate || 0) >= 50
                                  ? 'bg-yellow-500'
                                  : 'bg-red-500'
                              }`}
                              style={{ width: `${job.successRate || 0}%` }}
                            ></div>
                          </div>
                          <span className="text-sm text-gray-600">
                            {job.successRate?.toFixed(0) || 0}%
                          </span>
                        </div>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500">
                        {job.initiatedBy}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {new Date(job.createdAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-gray-500">
                No replay jobs yet. Replay messages from a DLQ topic to see them here.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
      <p className="text-sm text-gray-500">{label}</p>
      <p className={`text-2xl font-bold ${color}`}>{value}</p>
    </div>
  );
}
