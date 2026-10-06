import { useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { DlqTrendCard } from '../components/trend/DlqTrendCard';
import { SourceConsumersCard } from '../components/consumers/SourceConsumersCard';
import { replayWarning, useSourceConsumers } from '../hooks/useSourceConsumers';
import { dlqTopicsApi } from '../api/dlqTopics';
import { kafkaApi } from '../api/kafka';
import { replayApi } from '../api/replay';
import type { DlqMessage } from '../types';
import { usePermissions } from '../context/AuthContext';
import {
  ArrowLeft,
  Play,
  CheckSquare,
  Square,
  ChevronLeft,
  ChevronRight,
  AlertCircle,
  Copy,
  Check,
  Search,
  X,
  Download
} from 'lucide-react';

type TimePreset = 'any' | '1h' | '24h' | '7d' | 'custom';

const TIME_PRESETS: { value: TimePreset; label: string; hours?: number }[] = [
  { value: 'any', label: 'Any time' },
  { value: '1h', label: 'Last hour', hours: 1 },
  { value: '24h', label: 'Last 24 hours', hours: 24 },
  { value: '7d', label: 'Last 7 days', hours: 24 * 7 },
  { value: 'custom', label: 'Custom range...' },
];

const windowFormat = new Intl.DateTimeFormat(undefined, {
  day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

// "5 Oct, 11:00 – 5 Oct, 12:00", "since ..." or "before ..."
function describeWindow(from: string | null, to: string | null): string {
  if (from && to) return `${windowFormat.format(new Date(from))} – ${windowFormat.format(new Date(to))}`;
  if (from) return `since ${windowFormat.format(new Date(from))}`;
  return `before ${windowFormat.format(new Date(to!))}`;
}

// ISO time -> the value a datetime-local input wants (local time, no seconds)
function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromLocalInput(value: string): string | null {
  const date = new Date(value);
  return value && !Number.isNaN(date.getTime()) ? date.toISOString() : null;
}

export function DlqTopicDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { canOperate } = usePermissions();
  const [page, setPage] = useState(1);
  const [selectedMessages, setSelectedMessages] = useState<Set<string>>(new Set());
  const [expandedMessage, setExpandedMessage] = useState<DlqMessage | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [replayNotice, setReplayNotice] = useState<{ text: string; isError: boolean } | null>(null);

  // Filters: what's typed in the box vs. the search actually sent (after a short pause in typing)
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [errorType, setErrorType] = useState<string | null>(null);
  const [pendingOnly, setPendingOnly] = useState(false);
  const searchTimer = useRef<number | undefined>(undefined);
  // Time window on when a message landed in the DLQ (ISO times; null = open on that side)
  const [timePreset, setTimePreset] = useState<TimePreset>('any');
  const [timeFrom, setTimeFrom] = useState<string | null>(null);
  const [timeTo, setTimeTo] = useState<string | null>(null);
  const timeWindowInvalid = !!timeFrom && !!timeTo && timeFrom >= timeTo;
  // A window that makes no sense is not sent; the bar says what is wrong instead
  const from = timeWindowInvalid ? undefined : timeFrom ?? undefined;
  const to = timeWindowInvalid ? undefined : timeTo ?? undefined;
  const hasTimeWindow = timeFrom !== null || timeTo !== null;
  const hasFilters = search !== '' || errorType !== null || pendingOnly || hasTimeWindow;
  const filtersRef = useRef<HTMLDivElement>(null);

  // Who reads the source topic: checked before replaying, so nobody replays into a stopped service unawares
  const { data: sourceConsumers } = useSourceConsumers(id);

  // Test replay: '' sends to the source topic (the real replay), anything else is a topic to try the messages on
  const [sendTo, setSendTo] = useState('');
  const { data: kafkaTopics = [] } = useQuery({
    queryKey: ['kafkaTopics'],
    queryFn: kafkaApi.getTopics,
    enabled: canOperate,
  });

  const { data: topic } = useQuery({
    queryKey: ['dlqTopic', id],
    queryFn: () => dlqTopicsApi.getById(id!),
    enabled: !!id,
  });

  const { data: messagesData, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['dlqMessages', id, page, search, errorType, pendingOnly, from, to],
    queryFn: () => dlqTopicsApi.getMessages(id!, page, 10, {
      search,
      errorType: errorType ?? undefined,
      pendingOnly,
      from,
      to,
    }),
    enabled: !!id,
    // Keep showing the current page while the next one (or a new search) loads
    placeholderData: keepPreviousData,
  });

  // Any filter change starts again from page 1 with nothing selected
  const applyFilters = (update: () => void) => {
    update();
    setPage(1);
    setSelectedMessages(new Set());
  };

  const handleSearchChange = (value: string) => {
    setSearchInput(value);
    window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => applyFilters(() => setSearch(value.trim())), 400);
  };

  const handleErrorTypeClick = (type: string) => {
    applyFilters(() => setErrorType(current => (current === type ? null : type)));
  };

  const setTimeWindow = (preset: TimePreset, newFrom: string | null, newTo: string | null) => {
    applyFilters(() => {
      setTimePreset(preset);
      setTimeFrom(newFrom);
      setTimeTo(newTo);
    });
  };

  const handleTimePresetChange = (preset: TimePreset) => {
    const hours = TIME_PRESETS.find((p) => p.value === preset)?.hours;
    if (hours) {
      setTimeWindow(preset, new Date(Date.now() - hours * 3_600_000).toISOString(), null);
    } else if (preset === 'custom') {
      setTimeWindow('custom', timeFrom, timeTo); // keep what is set and show the two boxes
    } else {
      setTimeWindow('any', null, null);
    }
  };

  // Clicking a period in the trend chart lists the messages that failed in it
  const handleSelectPeriod = (periodFrom: Date, periodTo: Date) => {
    setTimeWindow('custom', periodFrom.toISOString(), periodTo.toISOString());
    filtersRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const clearFilters = () => {
    window.clearTimeout(searchTimer.current);
    setSearchInput('');
    applyFilters(() => {
      setSearch('');
      setErrorType(null);
      setPendingOnly(false);
      setTimePreset('any');
      setTimeFrom(null);
      setTimeTo(null);
    });
  };

  const { data: errorBreakdown } = useQuery({
    queryKey: ['errorBreakdown', id],
    queryFn: () => dlqTopicsApi.getErrorBreakdown(id!),
    enabled: !!id,
  });

  const showReplayNotice = (text: string, isError: boolean) => {
    setReplayNotice({ text, isError });
    setTimeout(() => setReplayNotice(null), 6000);
  };

  const replayMutation = useMutation({
    mutationFn: replayApi.replayBulk,
    onSuccess: (data) => {
      // After a test replay the same messages are still pending: keep them selected for the real one
      if (!data.replayJob.testReplay) {
        setSelectedMessages(new Set());
      }
      refetch();
      queryClient.invalidateQueries({ queryKey: ['replayHistory'] });
      queryClient.invalidateQueries({ queryKey: ['sourceConsumers', id] });
      showReplayNotice(data.message, data.replayJob.failed > 0);
    },
    onError: () => {
      showReplayNotice('Replay failed. Check the backend logs and Replay History.', true);
    },
  });

  // Messages that were already replayed can't be bulk-selected (avoids sending duplicates)
  const selectableMessages = messagesData?.messages.filter(m => !m.replayed) ?? [];
  const allSelected = selectableMessages.length > 0 && selectedMessages.size === selectableMessages.length;

  const handleSelectMessage = (message: DlqMessage) => {
    if (message.replayed) return;
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
    if (selectableMessages.length === 0) return;

    if (allSelected) {
      setSelectedMessages(new Set());
    } else {
      const allKeys = selectableMessages.map(m => `${m.partition}-${m.offset}`);
      setSelectedMessages(new Set(allKeys));
    }
  };

  const handleReplaySelected = async () => {
    if (selectedMessages.size === 0 || !id) return;

    const messages = Array.from(selectedMessages).map(key => {
      const [partition, offset] = key.split('-').map(Number);
      return { partition, offset };
    });

    // The consumers of the source topic only matter when the messages actually go there
    const warning = sendTo === '' ? replayWarning(sourceConsumers, messages.length) : null;
    if (warning && !window.confirm(`${warning}\n\nReplay anyway?`)) {
      return;
    }

    try {
      await replayMutation.mutateAsync({
        dlqTopicId: id,
        messages,
        targetTopic: sendTo || undefined,
      });
    } catch {
      // Error is shown by the mutation's onError handler
    }
  };

  const handleReplayFromModal = async (message: DlqMessage) => {
    const warning = replayWarning(sourceConsumers, 1);
    if (warning && !window.confirm(`${warning}\n\nReplay anyway?`)) {
      return;
    }
    if (message.replayed && !window.confirm(
      'This message was already replayed. Sending it again may process it twice in the source system. Replay anyway?'
    )) {
      return;
    }

    try {
      await replayMutation.mutateAsync({
        dlqTopicId: id!,
        messages: [{ partition: message.partition, offset: message.offset }],
        force: message.replayed === true,
      });
      setExpandedMessage(null);
    } catch {
      // Error is shown by the mutation's onError handler
    }
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

        {/* Trend: pending messages and new failures over time */}
        {id && <DlqTrendCard dlqTopicId={id} onSelectPeriod={handleSelectPeriod} />}

        {/* Error Breakdown */}
        {errorBreakdown && errorBreakdown.errorBreakdown.length > 0 && (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6 mb-6">
            <div className="flex items-baseline justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Error Breakdown</h3>
              <span className="text-xs text-gray-400 dark:text-gray-500">Click an error to filter the messages</span>
            </div>
            <div className="space-y-1">
              {errorBreakdown.errorBreakdown.slice(0, 5).map((item) => {
                const isActive = errorType === item.errorType;
                return (
                  <button
                    key={item.errorType}
                    onClick={() => handleErrorTypeClick(item.errorType)}
                    title={isActive ? 'Show all errors' : `Show only "${item.errorType}"`}
                    className={`w-full text-left px-3 py-2 rounded-lg transition-colors ${
                      isActive
                        ? 'bg-orange-50 dark:bg-orange-900/20 ring-1 ring-orange-400'
                        : 'hover:bg-gray-50 dark:hover:bg-gray-700/50'
                    }`}
                  >
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
                  </button>
                );
              })}
            </div>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-4">
              Total: {errorBreakdown.totalMessages} messages, {errorBreakdown.uniqueErrorTypes} error types
            </p>
          </div>
        )}

        {/* Where replays go: the services reading the source topic */}
        {id && <SourceConsumersCard dlqTopicId={id} />}

        {/* Replay result */}
        {replayNotice && (
          <div className={`mb-4 px-4 py-3 rounded-lg text-sm border ${
            replayNotice.isError
              ? 'bg-yellow-50 border-yellow-200 text-yellow-800 dark:bg-yellow-900/20 dark:border-yellow-700 dark:text-yellow-300'
              : 'bg-green-50 border-green-200 text-green-800 dark:bg-green-900/20 dark:border-green-700 dark:text-green-300'
          }`}>
            {replayNotice.text}
          </div>
        )}

        {/* Filters */}
        <div ref={filtersRef} className="flex flex-wrap items-center gap-3 mb-4 scroll-mt-4">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 dark:text-gray-500" />
            <input
              type="text"
              value={searchInput}
              onChange={(e) => handleSearchChange(e.target.value)}
              placeholder="Search key, payload or headers..."
              className="pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-orange-500 w-72"
            />
          </div>

          <label className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={pendingOnly}
              onChange={(e) => applyFilters(() => setPendingOnly(e.target.checked))}
              className="w-4 h-4 accent-orange-600"
            />
            Hide replayed
          </label>

          {/* When the message landed in the DLQ */}
          <select
            value={timePreset}
            onChange={(e) => handleTimePresetChange(e.target.value as TimePreset)}
            aria-label="Filter by time"
            className="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
          >
            {TIME_PRESETS.map((preset) => (
              <option key={preset.value} value={preset.value}>{preset.label}</option>
            ))}
          </select>

          {timePreset === 'custom' && (
            <span className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
              <input
                type="datetime-local"
                value={toLocalInput(timeFrom)}
                onChange={(e) => setTimeWindow('custom', fromLocalInput(e.target.value), timeTo)}
                aria-label="From"
                className="px-2 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white [color-scheme:light] dark:[color-scheme:dark]"
              />
              to
              <input
                type="datetime-local"
                value={toLocalInput(timeTo)}
                onChange={(e) => setTimeWindow('custom', timeFrom, fromLocalInput(e.target.value))}
                aria-label="To"
                className="px-2 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white [color-scheme:light] dark:[color-scheme:dark]"
              />
            </span>
          )}

          {timeWindowInvalid && (
            <span className="text-sm text-red-600 dark:text-red-400">The start must be before the end</span>
          )}

          {hasTimeWindow && !timeWindowInvalid && (
            <span className="inline-flex items-center gap-1 px-3 py-1 text-sm rounded-full bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300">
              Time: {describeWindow(timeFrom, timeTo)}
              <button onClick={() => setTimeWindow('any', null, null)} title="Remove time filter">
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          {errorType && (
            <span className="inline-flex items-center gap-1 px-3 py-1 text-sm rounded-full bg-orange-100 text-orange-800 dark:bg-orange-900/40 dark:text-orange-300">
              Error: {errorType}
              <button onClick={() => handleErrorTypeClick(errorType)} title="Remove error filter">
                <X className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          {hasFilters && (
            <button
              onClick={clearFilters}
              className="text-sm text-orange-600 hover:text-orange-700 font-medium"
            >
              Clear filters
            </button>
          )}

          {isFetching && !isLoading && (
            <span className="text-sm text-gray-400 dark:text-gray-500">Searching...</span>
          )}

          {/* Export: downloads every message matching the current filters, not just this page */}
          {id && (messagesData?.matchingMessages ?? 0) > 0 && (
            <div className="ml-auto flex items-center gap-2">
              <span className="text-sm text-gray-500 dark:text-gray-400">
                Export {messagesData?.matchingMessages}:
              </span>
              {(['csv', 'json'] as const).map(format => (
                <a
                  key={format}
                  href={dlqTopicsApi.exportUrl(id, format, { search, errorType: errorType ?? undefined, pendingOnly, from, to })}
                  download
                  title={`Download as ${format.toUpperCase()}`}
                  className="flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                >
                  <Download className="w-4 h-4" />
                  {format.toUpperCase()}
                </a>
              ))}
            </div>
          )}
        </div>

        {messagesData?.scanLimitReached && (
          <div className="mb-4 px-4 py-3 rounded-lg text-sm border bg-yellow-50 border-yellow-200 text-yellow-800 dark:bg-yellow-900/20 dark:border-yellow-700 dark:text-yellow-300">
            This DLQ is very large, so only the first 100,000 messages were searched.
          </div>
        )}

        {/* Actions Bar */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-4">
            {!canOperate ? (
              <span className="text-sm text-gray-400 dark:text-gray-500 italic">
                View only - replaying messages needs the operator role
              </span>
            ) : selectedMessages.size === 0 ? (
              <span className="text-sm text-gray-400 dark:text-gray-500 italic">
                Select messages to replay them to the source topic
              </span>
            ) : (
              <>
                <span className="text-sm text-gray-500 dark:text-gray-400">
                  {selectedMessages.size} selected
                </span>
                <label className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                  Send to
                  <select
                    value={sendTo}
                    onChange={(e) => setSendTo(e.target.value)}
                    className="px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-orange-500 focus:border-orange-500 max-w-64"
                  >
                    <option value="">{topic?.sourceTopic ?? 'source topic'} (source)</option>
                    {kafkaTopics
                      .map((kafkaTopic) => kafkaTopic.name)
                      .filter((name) => name !== topic?.sourceTopic && name !== topic?.dlqTopicName)
                      .map((name) => (
                        <option key={name} value={name}>{name} (test)</option>
                      ))}
                  </select>
                </label>
                <button
                  onClick={handleReplaySelected}
                  disabled={replayMutation.isPending}
                  title={sendTo ? 'The messages are copied to this topic and stay pending here' : undefined}
                  className={`flex items-center gap-2 px-4 py-2 text-white rounded-lg transition-colors disabled:opacity-50 ${
                    sendTo ? 'bg-blue-600 hover:bg-blue-700' : 'bg-green-600 hover:bg-green-700'
                  }`}
                >
                  <Play className="w-4 h-4" />
                  {sendTo ? `Test replay (${selectedMessages.size})` : `Replay Selected (${selectedMessages.size})`}
                </button>
              </>
            )}
          </div>
          <div className="text-sm text-gray-500 dark:text-gray-400">
            Page {messagesData?.currentPage || 1} of {messagesData?.totalPages || 1}
            {messagesData?.filtered
              ? ` (${messagesData.matchingMessages} matching of ${messagesData.totalMessages})`
              : ` (${messagesData?.totalMessages || 0} total, ${messagesData?.pendingMessages ?? 0} pending)`}
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
                    {canOperate && (
                    <th className="px-4 py-3 text-left">
                      <button
                        onClick={handleSelectAll}
                        title="Select all for replay"
                        className="flex items-center gap-1 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                      >
                        {allSelected ? (
                          <CheckSquare className="w-5 h-5" />
                        ) : (
                          <Square className="w-5 h-5" />
                        )}
                        <span className="text-xs font-medium uppercase tracking-wider">Replay</span>
                      </button>
                    </th>
                    )}
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
                        {canOperate && (
                        <td className="px-4 py-4">
                          <button
                            onClick={() => handleSelectMessage(message)}
                            disabled={message.replayed}
                            title={message.replayed ? 'Already replayed - open details to replay again' : undefined}
                            className="text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            {isSelected ? (
                              <CheckSquare className="w-5 h-5 text-orange-600" />
                            ) : (
                              <Square className="w-5 h-5" />
                            )}
                          </button>
                        </td>
                        )}
                        <td className="px-4 py-4 whitespace-nowrap font-mono text-sm text-gray-900 dark:text-gray-200">
                          <div className="flex items-center gap-2">
                            {message.offset}
                            {message.replayed && (
                              <span
                                title={message.replayedAt ? `Replayed ${new Date(message.replayedAt).toLocaleString()}` : 'Replayed'}
                                className="inline-flex px-2 py-0.5 text-xs font-sans font-medium rounded-full bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300"
                              >
                                Replayed
                              </span>
                            )}
                          </div>
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
                              {message.errorMessage || message.exceptionClass || 'Unknown error'}
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
                {hasFilters ? (
                  <>
                    No messages match these filters.{' '}
                    <button onClick={clearFilters} className="text-orange-600 hover:underline">Clear filters</button>
                  </>
                ) : (
                  'No messages in this DLQ topic.'
                )}
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
                  {expandedMessage.replayed && (
                    <div className="col-span-2">
                      <span className="text-gray-500 dark:text-gray-400">Replayed:</span>{' '}
                      <span className="text-green-700 dark:text-green-300">
                        {expandedMessage.replayedAt ? new Date(expandedMessage.replayedAt).toLocaleString() : 'Yes'}
                      </span>
                    </div>
                  )}
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
              {canOperate && (
                <button
                  onClick={() => handleReplayFromModal(expandedMessage)}
                  disabled={replayMutation.isPending}
                  className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
                >
                  <Play className="w-4 h-4" />
                  {expandedMessage.replayed ? 'Replay Again' : 'Replay This Message'}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
