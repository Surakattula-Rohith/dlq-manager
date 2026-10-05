import { AlertTriangle, CheckCircle, RefreshCw, XCircle } from 'lucide-react';
import type { ConsumerStatus, SourceConsumer } from '../../types';
import { useSourceConsumers } from '../../hooks/useSourceConsumers';

// Status is always an icon plus a word, never colour alone
const STATUS: Record<ConsumerStatus, { label: string; icon: typeof CheckCircle; className: string }> = {
  NOT_RUNNING: { label: 'Not running', icon: XCircle, className: 'text-red-600 dark:text-red-400' },
  BEHIND: { label: 'Behind', icon: AlertTriangle, className: 'text-amber-600 dark:text-amber-400' },
  REBALANCING: { label: 'Rebalancing', icon: RefreshCw, className: 'text-blue-600 dark:text-blue-400' },
  CAUGHT_UP: { label: 'Caught up', icon: CheckCircle, className: 'text-green-600 dark:text-green-400' },
};

/**
 * Who picks up replayed messages: the consumer groups of the DLQ's source topic.
 * Replaying into a service that is down or far behind just moves the problem.
 */
export function SourceConsumersCard({ dlqTopicId }: { dlqTopicId: string }) {
  const { data, isLoading, isError } = useSourceConsumers(dlqTopicId);

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-3">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
          Replays go to {data ? <span className="font-mono text-base">{data.sourceTopic}</span> : 'the source topic'}
        </h3>
        <span className="text-xs text-gray-400 dark:text-gray-500">Services reading it, and whether they keep up</span>
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">Checking consumers...</p>
      ) : isError || !data ? (
        <p className="text-sm text-gray-500 dark:text-gray-400">Could not check the consumers of the source topic.</p>
      ) : !data.topicExists ? (
        <p className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
          <XCircle className="w-4 h-4 flex-shrink-0" />
          "{data.sourceTopic}" doesn't exist in Kafka, so replays to it will fail.
        </p>
      ) : data.consumers.length === 0 ? (
        <p className="flex items-center gap-2 text-sm text-amber-700 dark:text-amber-400">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          No service has read "{data.sourceTopic}" yet - replayed messages will wait there until one does.
        </p>
      ) : (
        <ul className="divide-y divide-gray-100 dark:divide-gray-700">
          {data.consumers.map((consumer) => (
            <ConsumerRow key={consumer.groupId} consumer={consumer} />
          ))}
        </ul>
      )}
    </div>
  );
}

function ConsumerRow({ consumer }: { consumer: SourceConsumer }) {
  const status = STATUS[consumer.status];
  const Icon = status.icon;
  return (
    <li className="flex flex-wrap items-center gap-x-6 gap-y-1 py-2 text-sm">
      <span className={`flex items-center gap-1.5 w-32 font-medium ${status.className}`}>
        <Icon className="w-4 h-4 flex-shrink-0" />
        {status.label}
      </span>
      <span className="font-medium text-gray-900 dark:text-white min-w-48">{consumer.groupId}</span>
      <span className="text-gray-600 dark:text-gray-300 tabular-nums">
        {consumer.lag === 0
          ? 'nothing waiting'
          : `${consumer.lag.toLocaleString()} message${consumer.lag === 1 ? '' : 's'} waiting`}
      </span>
      <span className="text-gray-500 dark:text-gray-400">
        {consumer.members === 0
          ? 'no consumers running'
          : `${consumer.members} consumer${consumer.members === 1 ? '' : 's'} running`}
      </span>
    </li>
  );
}
