import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { alertsApi } from '../api/alerts';
import { notificationChannelsApi } from '../api/notificationChannels';
import { dlqTopicsApi } from '../api/dlqTopics';
import type { AlertRule, AlertType } from '../types';
import {
  Bell, Plus, Trash2, Edit2, Power, CheckCircle,
  Clock, AlertTriangle, Activity, Loader2, X, ChevronDown
} from 'lucide-react';
import { formatDateTime } from '../utils/date';

const cardClass = 'bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700';
const inputClass = 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-orange-500';
const labelClass = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

type Tab = 'rules' | 'history';

export function AlertsPage() {
  const [tab, setTab] = useState<Tab>('rules');
  const [showRuleModal, setShowRuleModal] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null);
  const [snoozeEventId, setSnoozeEventId] = useState<string | null>(null);

  const queryClient = useQueryClient();

  const { data: rules = [], isLoading: loadingRules } = useQuery({
    queryKey: ['alertRules'],
    queryFn: alertsApi.getRules,
  });

  const { data: eventsData, isLoading: loadingEvents } = useQuery({
    queryKey: ['alertEvents'],
    queryFn: alertsApi.getEvents,
    refetchInterval: 30_000,
  });

  const firingCount = eventsData?.firingCount ?? 0;

  const toggleMutation = useMutation({
    mutationFn: alertsApi.toggleRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alertRules'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: alertsApi.deleteRule,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alertRules'] }),
  });

  const acknowledgeMutation = useMutation({
    mutationFn: alertsApi.acknowledgeEvent,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alertEvents'] }),
  });

  const snoozeMutation = useMutation({
    mutationFn: ({ id, minutes }: { id: string; minutes: number }) =>
      alertsApi.snoozeEvent(id, minutes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alertEvents'] });
      setSnoozeEventId(null);
    },
  });

  return (
    <div className="min-h-screen">
      <Header
        title="Alerts"
        subtitle="Configure alerting rules and view notification history"
      />

      <div className="p-6">
        {/* Stats bar */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
          <StatCard label="Total Rules" value={rules.length} icon={Bell} color="orange" />
          <StatCard label="Active Rules" value={rules.filter(r => r.enabled).length} icon={Activity} color="green" />
          <StatCard label="Firing Alerts" value={firingCount} icon={AlertTriangle} color={firingCount > 0 ? 'red' : 'gray'} />
        </div>

        {/* Tabs */}
        <div className="flex gap-1 mb-6 border-b border-gray-200 dark:border-gray-700">
          {(['rules', 'history'] as Tab[]).map(t => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px capitalize transition-colors ${
                tab === t
                  ? 'border-orange-500 text-orange-600 dark:text-orange-400'
                  : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }`}
            >
              {t === 'rules' ? 'Alert Rules' : 'Alert History'}
              {t === 'history' && firingCount > 0 && (
                <span className="ml-2 px-1.5 py-0.5 text-xs bg-red-500 text-white rounded-full">{firingCount}</span>
              )}
            </button>
          ))}
        </div>

        {/* Rules tab */}
        {tab === 'rules' && (
          <div>
            <div className="flex justify-end mb-4">
              <button
                onClick={() => { setEditingRule(null); setShowRuleModal(true); }}
                className="flex items-center gap-2 px-4 py-2 bg-orange-500 text-white rounded-lg hover:bg-orange-600"
              >
                <Plus className="w-4 h-4" /> Add Rule
              </button>
            </div>

            <div className={cardClass}>
              {loadingRules ? (
                <div className="p-8 text-center text-gray-500 dark:text-gray-400">
                  <Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />
                  Loading rules...
                </div>
              ) : rules.length === 0 ? (
                <div className="p-8 text-center">
                  <Bell className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                  <p className="text-gray-500 dark:text-gray-400">No alert rules configured.</p>
                  <button
                    onClick={() => { setEditingRule(null); setShowRuleModal(true); }}
                    className="mt-3 text-orange-500 hover:text-orange-600 text-sm font-medium"
                  >
                    + Add your first rule
                  </button>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 dark:border-gray-700 text-left">
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Name</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Topic</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Type</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Condition</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Channel</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Status</th>
                        <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rules.map(rule => (
                        <tr key={rule.id} className="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-750">
                          <td className="px-4 py-3 font-medium text-gray-900 dark:text-white">{rule.name}</td>
                          <td className="px-4 py-3 text-gray-600 dark:text-gray-400 font-mono text-xs">{rule.dlqTopicName}</td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                              rule.alertType === 'THRESHOLD'
                                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                                : 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300'
                            }`}>
                              {rule.alertType === 'THRESHOLD' ? 'Threshold' : 'Time Window'}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-600 dark:text-gray-400 text-xs">
                            {rule.alertType === 'THRESHOLD'
                              ? `≥ ${rule.threshold} messages`
                              : `+${rule.threshold} in ${rule.windowMinutes}m`}
                          </td>
                          <td className="px-4 py-3 text-gray-600 dark:text-gray-400 text-xs">
                            {rule.notificationChannelName ? (
                              <span className="flex items-center gap-1">
                                <ChannelIcon type={rule.notificationChannelType} />
                                {rule.notificationChannelName}
                              </span>
                            ) : (
                              <span className="text-gray-400 dark:text-gray-600">—</span>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                              rule.enabled
                                ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                                : 'bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
                            }`}>
                              {rule.enabled ? 'Enabled' : 'Disabled'}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => toggleMutation.mutate(rule.id)}
                                title={rule.enabled ? 'Disable' : 'Enable'}
                                className="p-1 text-gray-400 hover:text-orange-500 dark:hover:text-orange-400"
                              >
                                <Power className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => { setEditingRule(rule); setShowRuleModal(true); }}
                                className="p-1 text-gray-400 hover:text-blue-500 dark:hover:text-blue-400"
                              >
                                <Edit2 className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => deleteMutation.mutate(rule.id)}
                                className="p-1 text-gray-400 hover:text-red-500 dark:hover:text-red-400"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}

        {/* History tab */}
        {tab === 'history' && (
          <div className={cardClass}>
            {loadingEvents ? (
              <div className="p-8 text-center text-gray-500 dark:text-gray-400">
                <Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />
                Loading events...
              </div>
            ) : !eventsData?.alertEvents.length ? (
              <div className="p-8 text-center">
                <CheckCircle className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                <p className="text-gray-500 dark:text-gray-400">No alert events yet.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 dark:border-gray-700 text-left">
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Rule</th>
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Topic</th>
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Status</th>
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Messages</th>
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Triggered</th>
                      <th className="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {eventsData.alertEvents.map(event => (
                      <tr key={event.id} className="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-750">
                        <td className="px-4 py-3 font-medium text-gray-900 dark:text-white">{event.alertRuleName}</td>
                        <td className="px-4 py-3 text-gray-600 dark:text-gray-400 font-mono text-xs">{event.dlqTopicName}</td>
                        <td className="px-4 py-3">
                          <EventStatusBadge status={event.status} snoozedUntil={event.snoozedUntil} />
                        </td>
                        <td className="px-4 py-3 text-gray-900 dark:text-white font-medium">{event.messageCount}</td>
                        <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                          {formatDateTime(event.triggeredAt)}
                        </td>
                        <td className="px-4 py-3">
                          {event.status === 'FIRING' && (
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => acknowledgeMutation.mutate(event.id)}
                                disabled={acknowledgeMutation.isPending}
                                className="flex items-center gap-1 px-2 py-1 text-xs bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded hover:bg-green-200 dark:hover:bg-green-900/50"
                              >
                                <CheckCircle className="w-3 h-3" /> Ack
                              </button>
                              <button
                                onClick={() => setSnoozeEventId(event.id)}
                                className="flex items-center gap-1 px-2 py-1 text-xs bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded hover:bg-yellow-200"
                              >
                                <Clock className="w-3 h-3" /> Snooze
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {showRuleModal && (
        <RuleModal
          editingRule={editingRule}
          onClose={() => setShowRuleModal(false)}
          onSaved={() => {
            setShowRuleModal(false);
            queryClient.invalidateQueries({ queryKey: ['alertRules'] });
          }}
        />
      )}

      {snoozeEventId && (
        <SnoozeModal
          onClose={() => setSnoozeEventId(null)}
          onConfirm={(minutes) => snoozeMutation.mutate({ id: snoozeEventId, minutes })}
          isPending={snoozeMutation.isPending}
        />
      )}
    </div>
  );
}

// ---- Sub-components ----

function StatCard({ label, value, icon: Icon, color }: {
  label: string; value: number; icon: React.ElementType;
  color: 'orange' | 'green' | 'red' | 'gray';
}) {
  const colors = {
    orange: 'bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400',
    green: 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400',
    red: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400',
    gray: 'bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400',
  };
  return (
    <div className={cardClass + ' p-4 flex items-center gap-4'}>
      <div className={`p-3 rounded-lg ${colors[color]}`}>
        <Icon className="w-5 h-5" />
      </div>
      <div>
        <p className="text-2xl font-bold text-gray-900 dark:text-white">{value}</p>
        <p className="text-sm text-gray-500 dark:text-gray-400">{label}</p>
      </div>
    </div>
  );
}

function EventStatusBadge({ status, snoozedUntil }: { status: string; snoozedUntil?: string }) {
  if (status === 'FIRING') return (
    <span className="flex items-center gap-1 px-2 py-0.5 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded-full text-xs font-medium w-fit">
      <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
      Firing
    </span>
  );
  if (status === 'ACKNOWLEDGED') return (
    <span className="px-2 py-0.5 bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 rounded-full text-xs font-medium">Acknowledged</span>
  );
  if (status === 'SNOOZED') return (
    <span
      className="px-2 py-0.5 bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-300 rounded-full text-xs font-medium"
      title={snoozedUntil ? `Until ${formatDateTime(snoozedUntil)}` : undefined}
    >
      Snoozed
    </span>
  );
  return null;
}

function ChannelIcon({ type }: { type?: string }) {
  const icons: Record<string, string> = { SLACK: '💬' };
  return <span>{icons[type ?? ''] ?? '📣'}</span>;
}

// ---- Rule Modal ----

function RuleModal({ editingRule, onClose, onSaved }: {
  editingRule: AlertRule | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(editingRule?.name ?? '');
  const [dlqTopicId, setDlqTopicId] = useState(editingRule?.dlqTopicId ?? '');
  const [alertType, setAlertType] = useState<AlertType>(editingRule?.alertType ?? 'THRESHOLD');
  const [threshold, setThreshold] = useState(String(editingRule?.threshold ?? 100));
  const [windowMinutes, setWindowMinutes] = useState(String(editingRule?.windowMinutes ?? 5));
  const [notificationChannelId, setNotificationChannelId] = useState(editingRule?.notificationChannelId ?? '');
  const [cooldownMinutes, setCooldownMinutes] = useState(String(editingRule?.cooldownMinutes ?? 30));
  const [enabled, setEnabled] = useState(editingRule?.enabled ?? true);
  const [error, setError] = useState('');

  const { data: topics = [] } = useQuery({ queryKey: ['dlqTopics'], queryFn: dlqTopicsApi.getAll });
  const { data: channels = [] } = useQuery({ queryKey: ['notificationChannels'], queryFn: notificationChannelsApi.getAll });

  const createMutation = useMutation({
    mutationFn: alertsApi.createRule,
    onSuccess: onSaved,
    onError: (e: Error) => setError(e.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof alertsApi.updateRule>[1] }) =>
      alertsApi.updateRule(id, data),
    onSuccess: onSaved,
    onError: (e: Error) => setError(e.message),
  });

  const isPending = createMutation.isPending || updateMutation.isPending;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!name.trim()) return setError('Name is required');
    if (!dlqTopicId && !editingRule) return setError('DLQ topic is required');

    const data = {
      name: name.trim(),
      alertType,
      threshold: parseInt(threshold),
      windowMinutes: alertType === 'TIME_WINDOW' ? parseInt(windowMinutes) : undefined,
      notificationChannelId: notificationChannelId || undefined,
      cooldownMinutes: parseInt(cooldownMinutes),
    };

    if (editingRule) {
      updateMutation.mutate({ id: editingRule.id, data: { ...data, enabled } });
    } else {
      createMutation.mutate({ ...data, dlqTopicId });
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            {editingRule ? 'Edit Alert Rule' : 'New Alert Rule'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={labelClass}>Rule Name</label>
            <input type="text" value={name} onChange={e => setName(e.target.value)}
              placeholder="e.g. High order DLQ volume" className={inputClass} />
          </div>

          {!editingRule && (
            <div>
              <label className={labelClass}>DLQ Topic</label>
              <div className="relative">
                <select value={dlqTopicId} onChange={e => setDlqTopicId(e.target.value)} className={inputClass + ' appearance-none pr-8'}>
                  <option value="">Select a topic…</option>
                  {topics.map(t => <option key={t.id} value={t.id}>{t.dlqTopicName}</option>)}
                </select>
                <ChevronDown className="absolute right-2 top-3 w-4 h-4 text-gray-400 pointer-events-none" />
              </div>
            </div>
          )}

          <div>
            <label className={labelClass}>Alert Type</label>
            <div className="grid grid-cols-2 gap-2">
              {(['THRESHOLD', 'TIME_WINDOW'] as AlertType[]).map(t => (
                <button key={t} type="button" onClick={() => setAlertType(t)}
                  className={`px-3 py-2 rounded-lg text-sm font-medium border transition-colors ${
                    alertType === t
                      ? 'bg-orange-500 text-white border-orange-500'
                      : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-orange-400'
                  }`}>
                  {t === 'THRESHOLD' ? 'Threshold' : 'Time Window'}
                </button>
              ))}
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              {alertType === 'THRESHOLD'
                ? 'Fire when current message count ≥ threshold.'
                : 'Fire when message count increases by ≥ threshold within the window.'}
            </p>
          </div>

          <div className={alertType === 'TIME_WINDOW' ? 'grid grid-cols-2 gap-3' : ''}>
            <div>
              <label className={labelClass}>Threshold (messages)</label>
              <input type="number" min="1" value={threshold} onChange={e => setThreshold(e.target.value)} className={inputClass} />
            </div>
            {alertType === 'TIME_WINDOW' && (
              <div>
                <label className={labelClass}>Window (minutes)</label>
                <input type="number" min="1" value={windowMinutes} onChange={e => setWindowMinutes(e.target.value)} className={inputClass} />
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Notification Channel</label>
              <div className="relative">
                <select value={notificationChannelId} onChange={e => setNotificationChannelId(e.target.value)} className={inputClass + ' appearance-none pr-8'}>
                  <option value="">None</option>
                  {channels.filter(c => c.enabled).map(c => (
                    <option key={c.id} value={c.id}>{c.name} ({c.type})</option>
                  ))}
                </select>
                <ChevronDown className="absolute right-2 top-3 w-4 h-4 text-gray-400 pointer-events-none" />
              </div>
            </div>
            <div>
              <label className={labelClass}>Cooldown (minutes)</label>
              <input type="number" min="1" value={cooldownMinutes} onChange={e => setCooldownMinutes(e.target.value)} className={inputClass} />
            </div>
          </div>

          {editingRule && (
            <div className="flex items-center gap-3">
              <button type="button" onClick={() => setEnabled(!enabled)}
                className={`relative w-10 h-6 rounded-full transition-colors ${enabled ? 'bg-orange-500' : 'bg-gray-300 dark:bg-gray-600'}`}>
                <span className={`absolute top-1 w-4 h-4 bg-white rounded-full shadow transition-all ${enabled ? 'left-5' : 'left-1'}`} />
              </button>
              <span className="text-sm text-gray-700 dark:text-gray-300">{enabled ? 'Enabled' : 'Disabled'}</span>
            </div>
          )}

          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
              Cancel
            </button>
            <button type="submit" disabled={isPending}
              className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-orange-500 text-white rounded-lg hover:bg-orange-600 disabled:opacity-50">
              {isPending && <Loader2 className="w-4 h-4 animate-spin" />}
              {editingRule ? 'Save Changes' : 'Create Rule'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ---- Snooze Modal ----

function SnoozeModal({ onClose, onConfirm, isPending }: {
  onClose: () => void;
  onConfirm: (minutes: number) => void;
  isPending: boolean;
}) {
  const options = [15, 30, 60, 120, 240];
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-80 mx-4 p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">Snooze Alert</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <X className="w-4 h-4" />
          </button>
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">Snooze for how long?</p>
        <div className="grid grid-cols-2 gap-2">
          {options.map(m => (
            <button key={m} onClick={() => onConfirm(m)} disabled={isPending}
              className="px-3 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-lg hover:border-orange-400 hover:text-orange-600 text-sm disabled:opacity-50">
              {m >= 60 ? `${m / 60}h` : `${m}m`}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
