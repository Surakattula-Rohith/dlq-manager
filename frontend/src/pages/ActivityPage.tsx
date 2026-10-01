import { useState } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, ScrollText } from 'lucide-react';
import { Header } from '../components/layout';
import { activityApi } from '../api/activity';
import type { ActivityAction } from '../types';
import { formatDateTime } from '../utils/date';

const PAGE_SIZE = 25;

const ACTION_LABELS: Record<ActivityAction, string> = {
  SIGNED_IN: 'Signed in',
  SIGNED_OUT: 'Signed out',
  SIGN_IN_FAILED: 'Failed sign-in',
  MESSAGES_REPLAYED: 'Replayed messages',
  ALERT_ACKNOWLEDGED: 'Acknowledged alert',
  ALERT_SNOOZED: 'Snoozed alert',
  DLQ_TOPIC_ADDED: 'Added DLQ topic',
  DLQ_TOPIC_UPDATED: 'Updated DLQ topic',
  DLQ_TOPIC_DELETED: 'Deleted DLQ topic',
  ALERT_RULE_CREATED: 'Created alert rule',
  ALERT_RULE_UPDATED: 'Updated alert rule',
  ALERT_RULE_ENABLED: 'Enabled alert rule',
  ALERT_RULE_DISABLED: 'Disabled alert rule',
  ALERT_RULE_DELETED: 'Deleted alert rule',
  CHANNEL_CREATED: 'Added Slack channel',
  CHANNEL_UPDATED: 'Updated Slack channel',
  CHANNEL_DELETED: 'Deleted Slack channel',
  KAFKA_SETTINGS_CHANGED: 'Changed Kafka settings',
};

const ACTION_GROUPS: { label: string; actions: ActivityAction[] }[] = [
  { label: 'Replays', actions: ['MESSAGES_REPLAYED'] },
  { label: 'Alerts', actions: ['ALERT_ACKNOWLEDGED', 'ALERT_SNOOZED'] },
  { label: 'Sign-in', actions: ['SIGNED_IN', 'SIGNED_OUT', 'SIGN_IN_FAILED'] },
  { label: 'DLQ topics', actions: ['DLQ_TOPIC_ADDED', 'DLQ_TOPIC_UPDATED', 'DLQ_TOPIC_DELETED'] },
  {
    label: 'Alert rules',
    actions: ['ALERT_RULE_CREATED', 'ALERT_RULE_UPDATED', 'ALERT_RULE_ENABLED', 'ALERT_RULE_DISABLED', 'ALERT_RULE_DELETED'],
  },
  { label: 'Slack channels', actions: ['CHANNEL_CREATED', 'CHANNEL_UPDATED', 'CHANNEL_DELETED'] },
  { label: 'Settings', actions: ['KAFKA_SETTINGS_CHANGED'] },
];

function actionStyle(action: ActivityAction): string {
  if (action === 'SIGN_IN_FAILED' || action.endsWith('_DELETED')) {
    return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300';
  }
  if (action === 'MESSAGES_REPLAYED') {
    return 'bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300';
  }
  if (action === 'ALERT_ACKNOWLEDGED' || action === 'ALERT_SNOOZED') {
    return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-300';
  }
  if (action === 'SIGNED_IN' || action === 'SIGNED_OUT') {
    return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300';
  }
  return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300';
}

export function ActivityPage() {
  const [page, setPage] = useState(1);
  const [username, setUsername] = useState('');
  const [action, setAction] = useState<ActivityAction | ''>('');

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['activity', page, username, action],
    queryFn: () => activityApi.getActivity(page, PAGE_SIZE, username, action || undefined),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  const selectClass = "px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white text-sm focus:ring-2 focus:ring-orange-500 focus:border-orange-500";
  const totalPages = data?.pagination.totalPages ?? 1;

  return (
    <div className="min-h-screen">
      <Header
        title="Activity"
        subtitle="Who did what, and when"
        onRefresh={() => refetch()}
        isRefreshing={isFetching}
      />

      <div className="p-6">
        {/* Filters */}
        <div className="flex flex-wrap items-center gap-3 mb-4">
          <select
            value={username}
            onChange={(e) => { setUsername(e.target.value); setPage(1); }}
            className={selectClass}
            aria-label="Filter by person"
          >
            <option value="">Everyone</option>
            {data?.usernames.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>

          <select
            value={action}
            onChange={(e) => { setAction(e.target.value as ActivityAction | ''); setPage(1); }}
            className={selectClass}
            aria-label="Filter by action"
          >
            <option value="">All actions</option>
            {ACTION_GROUPS.map((group) => (
              <optgroup key={group.label} label={group.label}>
                {group.actions.map((a) => (
                  <option key={a} value={a}>{ACTION_LABELS[a]}</option>
                ))}
              </optgroup>
            ))}
          </select>

          {(username || action) && (
            <button
              onClick={() => { setUsername(''); setAction(''); setPage(1); }}
              className="text-sm text-orange-600 hover:underline"
            >
              Clear filters
            </button>
          )}

          <span className="ml-auto text-sm text-gray-500 dark:text-gray-400">
            {data?.pagination.totalItems ?? 0} entries
          </span>
        </div>

        {/* Activity table */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">Loading activity...</div>
            ) : data && data.activity.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50 dark:bg-gray-700">
                  <tr>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">When</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Who</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">What</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                  {data.activity.map((entry) => (
                    <tr key={entry.id} className="hover:bg-gray-50 dark:hover:bg-gray-700 align-top">
                      <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                        {formatDateTime(entry.occurredAt)}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <div className="w-6 h-6 rounded-full bg-orange-600 text-white flex items-center justify-center text-xs font-semibold uppercase">
                            {entry.username.charAt(0)}
                          </div>
                          <span className="text-sm font-medium text-gray-900 dark:text-white">{entry.username}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex px-2 py-0.5 text-xs font-medium rounded-full whitespace-nowrap ${actionStyle(entry.action)}`}>
                          {ACTION_LABELS[entry.action] ?? entry.action}
                        </span>
                        {entry.target && (
                          <span className="ml-2 text-sm font-medium text-gray-900 dark:text-white">{entry.target}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400 break-words max-w-md">
                        {entry.details ?? ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center">
                <ScrollText className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                <p className="text-gray-500 dark:text-gray-400">
                  {username || action ? 'No activity matches these filters.' : 'No activity yet.'}
                </p>
              </div>
            )}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200 dark:border-gray-700">
              <button
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={page <= 1}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 dark:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" /> Newer
              </button>
              <span className="text-sm text-gray-500 dark:text-gray-400">Page {page} of {totalPages}</span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={page >= totalPages}
                className="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 dark:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Older <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
