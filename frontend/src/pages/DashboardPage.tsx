import { useQuery } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { dlqTopicsApi } from '../api/dlqTopics';
import { replayApi } from '../api/replay';
import {
  Inbox,
  AlertTriangle,
  CheckCircle,
  TrendingUp,
  ArrowRight
} from 'lucide-react';
import { Link } from 'react-router-dom';

export function DashboardPage() {
  const { data: dlqTopics, isLoading: loadingTopics, refetch } = useQuery({
    queryKey: ['dlqTopics'],
    queryFn: dlqTopicsApi.getAll,
  });

  const { data: replayHistory, isLoading: loadingHistory } = useQuery({
    queryKey: ['replayHistory'],
    queryFn: replayApi.getHistory,
  });

  const isLoading = loadingTopics || loadingHistory;

  const stats = {
    totalDlqs: dlqTopics?.length || 0,
    activeTopics: dlqTopics?.filter(t => t.status === 'ACTIVE').length || 0,
    totalReplays: replayHistory?.length || 0,
    successfulReplays: replayHistory?.filter(r => r.status === 'COMPLETED').length || 0,
  };

  return (
    <div className="min-h-screen">
      <Header
        title="Dashboard"
        subtitle="Overview of your Dead Letter Queues"
        onRefresh={() => refetch()}
        isRefreshing={isLoading}
      />

      <div className="p-6">
        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
          <SummaryCard
            title="DLQ Topics"
            value={stats.totalDlqs}
            subtitle={`${stats.activeTopics} active`}
            icon={Inbox}
            iconColor="text-blue-500"
            bgColor="bg-blue-50"
          />
          <SummaryCard
            title="Total Replays"
            value={stats.totalReplays}
            subtitle="All time"
            icon={TrendingUp}
            iconColor="text-green-500"
            bgColor="bg-green-50"
          />
          <SummaryCard
            title="Successful"
            value={stats.successfulReplays}
            subtitle={`${stats.totalReplays > 0 ? Math.round((stats.successfulReplays / stats.totalReplays) * 100) : 0}% success rate`}
            icon={CheckCircle}
            iconColor="text-emerald-500"
            bgColor="bg-emerald-50"
          />
          <SummaryCard
            title="Active Alerts"
            value={0}
            subtitle="Coming in Phase 4"
            icon={AlertTriangle}
            iconColor="text-orange-500"
            bgColor="bg-orange-50"
          />
        </div>

        {/* DLQ Topics Table */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 mb-6">
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">DLQ Topics</h2>
            <Link
              to="/dlq-topics"
              className="text-sm text-orange-600 hover:text-orange-700 flex items-center gap-1"
            >
              View all <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500">Loading...</div>
            ) : dlqTopics && dlqTopics.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">DLQ Topic</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source Topic</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Detection</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {dlqTopics.slice(0, 5).map((topic) => (
                    <tr key={topic.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="font-medium text-gray-900">{topic.dlqTopicName}</span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500">
                        {topic.sourceTopic}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                          topic.detectionType === 'AUTO'
                            ? 'bg-purple-100 text-purple-700'
                            : 'bg-gray-100 text-gray-700'
                        }`}>
                          {topic.detectionType}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex items-center gap-1 px-2 py-1 text-xs font-medium rounded-full ${
                          topic.status === 'ACTIVE'
                            ? 'bg-green-100 text-green-700'
                            : 'bg-yellow-100 text-yellow-700'
                        }`}>
                          <span className={`w-2 h-2 rounded-full ${
                            topic.status === 'ACTIVE' ? 'bg-green-500' : 'bg-yellow-500'
                          }`}></span>
                          {topic.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <Link
                          to={`/dlq-topics/${topic.id}`}
                          className="text-orange-600 hover:text-orange-700 text-sm font-medium"
                        >
                          View Messages
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-gray-500">
                No DLQ topics registered yet.{' '}
                <Link to="/dlq-topics" className="text-orange-600 hover:underline">
                  Add one now
                </Link>
              </div>
            )}
          </div>
        </div>

        {/* Recent Replays */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Recent Replays</h2>
            <Link
              to="/replay-history"
              className="text-sm text-orange-600 hover:text-orange-700 flex items-center gap-1"
            >
              View all <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="overflow-x-auto">
            {loadingHistory ? (
              <div className="p-8 text-center text-gray-500">Loading...</div>
            ) : replayHistory && replayHistory.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Job ID</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Initiated By</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Messages</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {replayHistory.slice(0, 5).map((job) => (
                    <tr key={job.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className="font-mono text-sm text-gray-900">{job.id.slice(0, 8)}...</span>
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500">
                        {job.initiatedBy}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500">
                        {job.succeeded}/{job.totalMessages}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap">
                        <StatusBadge status={job.status} />
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500 text-sm">
                        {new Date(job.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-gray-500">
                No replay jobs yet.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

interface SummaryCardProps {
  title: string;
  value: number;
  subtitle: string;
  icon: React.ElementType;
  iconColor: string;
  bgColor: string;
}

function SummaryCard({ title, value, subtitle, icon: Icon, iconColor, bgColor }: SummaryCardProps) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-gray-500">{title}</p>
          <p className="text-3xl font-bold text-gray-900 mt-1">{value}</p>
          <p className="text-sm text-gray-500 mt-1">{subtitle}</p>
        </div>
        <div className={`p-3 rounded-lg ${bgColor}`}>
          <Icon className={`w-6 h-6 ${iconColor}`} />
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const statusStyles: Record<string, string> = {
    COMPLETED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    IN_PROGRESS: 'bg-blue-100 text-blue-700',
    PENDING: 'bg-gray-100 text-gray-700',
    PARTIALLY_COMPLETED: 'bg-yellow-100 text-yellow-700',
  };

  return (
    <span className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${statusStyles[status] || statusStyles.PENDING}`}>
      {status.replace('_', ' ')}
    </span>
  );
}
