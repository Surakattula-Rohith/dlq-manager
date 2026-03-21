import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { dlqTopicsApi } from '../api/dlqTopics';
import { replayApi } from '../api/replay';
import type { DlqMessage } from '../types';
import {
  ArrowLeft,
  Play,
  CheckSquare,
  Square,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
  Copy,
  Check
} from 'lucide-react';

export function DlqTopicDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [selectedMessages, setSelectedMessages] = useState<Set<string>>(new Set());
  const [expandedMessage, setExpandedMessage] = useState<DlqMessage | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  const { data: topic } = useQuery({
    queryKey: ['dlqTopic', id],
    queryFn: () => dlqTopicsApi.getById(id!),
    enabled: !!id,
  });

  const { data: messagesData, isLoading, refetch } = useQuery({
    queryKey: ['dlqMessages', id, page],
    queryFn: () => dlqTopicsApi.getMessages(id!, page, 10),
    enabled: !!id,
  });

  const { data: errorBreakdown } = useQuery({
    queryKey: ['errorBreakdown', id],
    queryFn: () => dlqTopicsApi.getErrorBreakdown(id!),
    enabled: !!id,
  });

  const replayMutation = useMutation({
    mutationFn: replayApi.replayBulk,
    onSuccess: () => {
      setSelectedMessages(new Set());
      refetch();
      queryClient.invalidateQueries({ queryKey: ['replayHistory'] });
    },
  });

  const handleSelectMessage = (message: DlqMessage) => {
    const key = `${message.partition}-${message.offset}`;
    const newSelection = new Set(selectedMessages);
    if (newSelection.has(key)) {
      newSelection.delete(key);
    } else {
      newSelection.add(key);
    }
    setSelectedMessages(newSelection);
  };

  const handleSelectAll = () => {
    if (!messagesData?.messages) return;

    if (selectedMessages.size === messagesData.messages.length) {
      setSelectedMessages(new Set());
    } else {
      const allKeys = messagesData.messages.map(m => `${m.partition}-${m.offset}`);
      setSelectedMessages(new Set(allKeys));
    }
  };

  const handleReplaySelected = async () => {
    if (selectedMessages.size === 0 || !id) return;

    const messages = Array.from(selectedMessages).map(key => {
      const [partition, offset] = key.split('-').map(Number);
      return { partition, offset };
    });

    await replayMutation.mutateAsync({
      dlqTopicId: id,
      messages,
      initiatedBy: 'web-user',
    });
  };

  const copyToClipboard = (text: string, field: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  };

  return (
    <div className="min-h-screen">
      <Header
        title={topic?.dlqTopicName || 'Loading...'}
        subtitle={`Source: ${topic?.sourceTopic || '...'}`}
        onRefresh={() => refetch()}
        isRefreshing={isLoading}
      />

      <div className="p-6">
        {/* Back link */}
        <Link
          to="/dlq-topics"
          className="inline-flex items-center gap-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to DLQ Topics
        </Link>

        {/* Error Breakdown */}
        {errorBreakdown && errorBreakdown.errorBreakdown.length > 0 && (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Error Breakdown</h3>
            <div className="space-y-3">
              {errorBreakdown.errorBreakdown.slice(0, 5).map((item, index) => (
                <div key={index} className="flex items-center gap-4">
                  <div className="flex-1">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm font-medium text-gray-700 dark:text-gray-300 truncate">{item.errorType}</span>
                      <span className="text-sm text-gray-500 dark:text-gray-400">{item.count} ({item.percentage.toFixed(1)}%)</span>
                    </div>
                    <div className="w-full bg-gray-200 dark:bg-gray-600 rounded-full h-2">
                      <div
                        className="bg-orange-500 h-2 rounded-full"
                        style={{ width: `${item.percentage}%` }}
                      ></div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-4">
              Total: {errorBreakdown.totalMessages} messages, {errorBreakdown.uniqueErrorTypes} error types
            </p>
          </div>
        )}

        {/* Actions Bar */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-4">
            {selectedMessages.size === 0 ? (
              <span className="text-sm text-gray-400 dark:text-gray-500 italic">
                Select messages to replay them to the source topic
              </span>
            ) : (
              <>
                <span className="text-sm text-gray-500 dark:text-gray-400">
                  {selectedMessages.size} selected
                </span>
                <button
                  onClick={handleReplaySelected}
                  disabled={replayMutation.isPending}
                  className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors disabled:opacity-50"
                >
                  <Play className="w-4 h-4" />
                  Replay Selected ({selectedMessages.size})
                </button>
              </>
            )}
          </div>
          <div className="text-sm text-gray-500 dark:text-gray-400">
            Page {messagesData?.currentPage || 1} of {messagesData?.totalPages || 1}
            ({messagesData?.totalMessages || 0} total)
          </div>
        </div>

        {/* Messages Table */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">Loading messages...</div>
            ) : messagesData?.messages && messagesData.messages.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50 dark:bg-gray-700">
                  <tr>
                    <th className="px-4 py-3 text-left">
                      <button
                        onClick={handleSelectAll}
                        title="Select all for replay"
                        className="flex items-center gap-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                      >
                        {selectedMessages.size === messagesData.messages.length ? (
                          <CheckSquare className="w-5 h-5" />
                        ) : (
                          <Square className="w-5 h-5" />
                        )}
                        <span className="text-xs font-medium uppercase tracking-wider">Replay</span>
                      </button>
                    </th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Offset</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Partition</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Key</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Error</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Timestamp</th>
                    <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                  {messagesData.messages.map((message) => {
                    const key = `${message.partition}-${message.offset}`;
                    const isSelected = selectedMessages.has(key);

                    return (
                      <tr
                        key={key}
                        className={`hover:bg-gray-50 dark:hover:bg-gray-700 ${isSelected ? 'bg-orange-50 dark:bg-orange-900/20' : ''}`}
                      >
                        <td className="px-4 py-4">
                          <button
                            onClick={() => handleSelectMessage(message)}
                            className="text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                          >
                            {isSelected ? (
                              <CheckSquare className="w-5 h-5 text-orange-600" />
                            ) : (
                              <Square className="w-5 h-5" />
                            )}
                          </button>
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap font-mono text-sm text-gray-900 dark:text-gray-200">
                          {message.offset}
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-gray-500 dark:text-gray-400">
                          {message.partition}
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-gray-900 dark:text-white font-medium">
                          {message.key || '-'}
                        </td>
                        <td className="px-4 py-4 max-w-xs">
                          <div className="flex items-center gap-2">
                            <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />
                            <span className="text-sm text-gray-700 dark:text-gray-300 truncate">
                              {message.errorMessage || 'Unknown error'}
                            </span>
                          </div>
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                          {new Date(message.timestamp).toLocaleString()}
                        </td>
                        <td className="px-4 py-4 whitespace-nowrap">
                          <button
                            onClick={() => setExpandedMessage(message)}
                            className="text-orange-600 hover:text-orange-700 text-sm font-medium"
                          >
                            View Details
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">
                No messages in this DLQ topic.
              </div>
            )}
          </div>

          {/* Pagination */}
          {messagesData && messagesData.totalPages > 1 && (
            <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <button
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1}
                className="flex items-center gap-2 px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
                Previous
              </button>
              <div className="flex items-center gap-2">
                {Array.from({ length: Math.min(5, messagesData.totalPages) }, (_, i) => {
                  const pageNum = i + 1;
                  return (
                    <button
                      key={pageNum}
                      onClick={() => setPage(pageNum)}
                      className={`w-8 h-8 rounded-lg ${
                        page === pageNum
                          ? 'bg-orange-600 text-white'
                          : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
                      }`}
                    >
                      {pageNum}
                    </button>
                  );
                })}
                {messagesData.totalPages > 5 && (
                  <>
                    <span className="text-gray-400 dark:text-gray-500">...</span>
                    <button
                      onClick={() => setPage(messagesData.totalPages)}
                      className={`w-8 h-8 rounded-lg ${
                        page === messagesData.totalPages
                          ? 'bg-orange-600 text-white'
                          : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
                      }`}
                    >
                      {messagesData.totalPages}
                    </button>
                  </>
                )}
              </div>
              <button
                onClick={() => setPage(p => Math.min(messagesData.totalPages, p + 1))}
                disabled={page === messagesData.totalPages}
                className="flex items-center gap-2 px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Message Detail Modal */}
      {expandedMessage && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Message Detail</h2>
              <button
                onClick={() => setExpandedMessage(null)}
                className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              >
                ×
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Metadata */}
              <div>
                <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Metadata</h3>
                <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4 grid grid-cols-2 gap-4 text-sm">
                  <div><span className="text-gray-500 dark:text-gray-400">Offset:</span> <span className="font-mono text-gray-900 dark:text-white">{expandedMessage.offset}</span></div>
                  <div><span className="text-gray-500 dark:text-gray-400">Partition:</span> <span className="text-gray-900 dark:text-white">{expandedMessage.partition}</span></div>
                  <div><span className="text-gray-500 dark:text-gray-400">Key:</span> <span className="text-gray-900 dark:text-white">{expandedMessage.key || '-'}</span></div>
                  <div><span className="text-gray-500 dark:text-gray-400">Timestamp:</span> <span className="text-gray-900 dark:text-white">{new Date(expandedMessage.timestamp).toLocaleString()}</span></div>
                </div>
              </div>

              {/* Error Info */}
              <div>
                <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">Error Information</h3>
                <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-4">
                  <p className="text-red-700 dark:text-red-300">{expandedMessage.errorMessage || 'No error message'}</p>
                  {expandedMessage.exceptionClass && (
                    <p className="text-sm text-red-600 dark:text-red-400 mt-2 font-mono">{expandedMessage.exceptionClass}</p>
                  )}
                </div>
              </div>

              {/* Headers */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">Headers</h3>
                  <button
                    onClick={() => copyToClipboard(JSON.stringify(expandedMessage.headers, null, 2), 'headers')}
                    className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 flex items-center gap-1"
                  >
                    {copiedField === 'headers' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    Copy
                  </button>
                </div>
                <pre className="bg-gray-900 text-green-400 rounded-lg p-4 text-sm overflow-x-auto font-mono">
                  {JSON.stringify(expandedMessage.headers, null, 2)}
                </pre>
              </div>

              {/* Payload */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">Payload</h3>
                  <button
                    onClick={() => copyToClipboard(expandedMessage.payload, 'payload')}
                    className="text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 flex items-center gap-1"
                  >
                    {copiedField === 'payload' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    Copy
                  </button>
                </div>
                <pre className="bg-gray-900 text-green-400 rounded-lg p-4 text-sm overflow-x-auto font-mono max-h-64">
                  {(() => {
                    try {
                      return JSON.stringify(JSON.parse(expandedMessage.payload), null, 2);
                    } catch {
                      return expandedMessage.payload;
                    }
                  })()}
                </pre>
              </div>
            </div>
            <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex justify-end gap-3">
              <button
                onClick={() => setExpandedMessage(null)}
                className="px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                Close
              </button>
              <button
                onClick={async () => {
                  await replayMutation.mutateAsync({
                    dlqTopicId: id!,
                    messages: [{ partition: expandedMessage.partition, offset: expandedMessage.offset }],
                    initiatedBy: 'web-user',
                  });
                  setExpandedMessage(null);
                }}
                disabled={replayMutation.isPending}
                className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                <Play className="w-4 h-4" />
                Replay This Message
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
