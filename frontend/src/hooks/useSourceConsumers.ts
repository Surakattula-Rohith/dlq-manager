import { useQuery } from '@tanstack/react-query';
import { dlqTopicsApi } from '../api/dlqTopics';
import type { SourceConsumer, SourceConsumers } from '../types';

// A consumer this far behind gets a warning before a replay (a little lag is normal on a busy topic)
const FAR_BEHIND = 1_000;

/**
 * Consumer groups of the DLQ's source topic. The server remembers the answer for 30 seconds,
 * so checking every 30 seconds here keeps the page current without extra load on Kafka.
 */
export function useSourceConsumers(dlqTopicId: string | undefined) {
  return useQuery({
    queryKey: ['sourceConsumers', dlqTopicId],
    queryFn: () => dlqTopicsApi.getSourceConsumers(dlqTopicId!),
    enabled: !!dlqTopicId,
    refetchInterval: 30_000,
    retry: false,
  });
}

/**
 * What an operator should know before replaying, or null if the consumers look fine
 */
export function replayWarning(info: SourceConsumers | undefined, messageCount: number): string | null {
  if (!info) {
    return null;
  }
  const topic = info.sourceTopic;
  const replayed = messageCount === 1 ? 'the replayed message' : `the ${messageCount} replayed messages`;

  if (!info.topicExists) {
    return `The source topic "${topic}" doesn't exist in Kafka, so the replay will fail.`;
  }
  if (info.consumers.length === 0) {
    return `No service has read "${topic}" yet, so ${replayed} will wait there until one does.`;
  }

  const stopped = info.consumers.filter((consumer) => consumer.status === 'NOT_RUNNING');
  if (stopped.length > 0) {
    return `${names(stopped)} ${stopped.length === 1 ? 'is' : 'are'} not running, so ${replayed} `
      + `will wait in "${topic}" until ${stopped.length === 1 ? 'it starts' : 'they start'}.`;
  }

  const farBehind = info.consumers.filter((consumer) => consumer.status === 'BEHIND' && consumer.lag >= FAR_BEHIND);
  if (farBehind.length > 0) {
    const lag = Math.max(...farBehind.map((consumer) => consumer.lag)).toLocaleString();
    return `${names(farBehind)} ${farBehind.length === 1 ? 'is' : 'are'} ${lag} messages behind, `
      + `so ${replayed} will only be processed after those.`;
  }
  return null;
}

function names(consumers: SourceConsumer[]): string {
  const shown = consumers.slice(0, 2).map((consumer) => consumer.groupId).join(' and ');
  return consumers.length > 2 ? `${shown} and ${consumers.length - 2} more` : shown;
}
