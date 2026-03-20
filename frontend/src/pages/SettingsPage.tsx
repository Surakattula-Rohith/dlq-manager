import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { kafkaApi } from '../api/kafka';
import { notificationChannelsApi } from '../api/notificationChannels';
import type { ConnectionTestResult } from '../api/kafka';
import type { NotificationChannel, NotificationChannelType } from '../types';
import { Server, Database, CheckCircle, XCircle, Loader2, Plug, Save, Plus, Trash2, Edit2, Send, X, ChevronDown } from 'lucide-react';

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
        <div className="p-6 text-gray-500 dark:text-gray-400">Loading configuration...</div>
      </div>
    );
  }

  const cardClass = "bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700";
  const inputClass = "w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-orange-500";
  const labelClass = "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1";

  return (
    <div className="min-h-screen">
      <Header
        title="Settings"
        subtitle="Configure your DLQ Manager"
      />

      <div className="p-6">
        {/* First-time setup banner */}
        {isFirstTime && (
          <div className="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-700 rounded-lg p-4 mb-6">
            <h3 className="font-semibold text-orange-800 dark:text-orange-300">Welcome to DLQ Manager</h3>
            <p className="text-sm text-orange-700 dark:text-orange-400 mt-1">
              To get started, enter your Kafka bootstrap servers below and test the connection.
            </p>
          </div>
        )}

        {/* Kafka Configuration */}
        <div className={`${cardClass} p-6 mb-6`}>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Kafka Configuration</h3>

          <div className="space-y-4">
            <div>
              <label className={labelClass}>
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
                className={inputClass}
              />
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
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
                className="flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 disabled:opacity-50 disabled:cursor-not-allowed"
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
              <div className={`rounded-lg p-3 ${testResult.success ? 'bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700' : 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700'}`}>
                <div className="flex items-center gap-2">
                  {testResult.success ? (
                    <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400" />
                  ) : (
                    <XCircle className="w-5 h-5 text-red-600 dark:text-red-400" />
                  )}
                  <span className={`font-medium ${testResult.success ? 'text-green-700 dark:text-green-300' : 'text-red-700 dark:text-red-300'}`}>
                    {testResult.success ? 'Connection successful' : 'Connection failed'}
                  </span>
                </div>
                {testResult.success && (
                  <p className="text-sm text-green-600 dark:text-green-400 mt-1">
                    Cluster: {testResult.clusterId} | Brokers: {testResult.brokerCount}
                  </p>
                )}
                {!testResult.success && testResult.error && (
                  <p className="text-sm text-red-600 dark:text-red-400 mt-1">{testResult.error}</p>
                )}
              </div>
            )}

            {/* Save success feedback */}
            {saveSuccess && (
              <div className="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-700 rounded-lg p-3">
                <div className="flex items-center gap-2">
                  <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400" />
                  <span className="font-medium text-green-700 dark:text-green-300">Configuration saved successfully</span>
                </div>
              </div>
            )}

            {/* Save error feedback */}
            {saveMutation.isError && (
              <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-3">
                <div className="flex items-center gap-2">
                  <XCircle className="w-5 h-5 text-red-600 dark:text-red-400" />
                  <span className="font-medium text-red-700 dark:text-red-300">Failed to save configuration</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Connection Status */}
        <div className={`${cardClass} p-6 mb-6`}>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Connection Status</h3>

          <div className="flex items-center gap-4 mb-6">
            <div className={`p-3 rounded-lg ${isConnected ? 'bg-green-100 dark:bg-green-900/30' : 'bg-red-100 dark:bg-red-900/30'}`}>
              {isConnected ? (
                <CheckCircle className="w-6 h-6 text-green-600 dark:text-green-400" />
              ) : (
                <XCircle className="w-6 h-6 text-red-600 dark:text-red-400" />
              )}
            </div>
            <div>
              <p className={`font-medium ${isConnected ? 'text-green-700 dark:text-green-300' : 'text-red-700 dark:text-red-300'}`}>
                {isConnected ? 'Connected to Kafka' : 'Connection Failed'}
              </p>
              <p className="text-sm text-gray-500 dark:text-gray-400">
                {isConnected
                  ? `Cluster ID: ${clusterInfo?.clusterId || 'N/A'}`
                  : 'Unable to connect to Kafka cluster'}
              </p>
            </div>
          </div>

          {loadingCluster ? (
            <div className="text-gray-500 dark:text-gray-400">Loading cluster info...</div>
          ) : isConnected && clusterInfo ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Server className="w-5 h-5 text-gray-500 dark:text-gray-400" />
                  <h4 className="font-medium text-gray-900 dark:text-white">Cluster ID</h4>
                </div>
                <p className="font-mono text-sm text-gray-700 dark:text-gray-300 break-all">{clusterInfo.clusterId}</p>
              </div>

              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-5 h-5 text-gray-500 dark:text-gray-400" />
                  <h4 className="font-medium text-gray-900 dark:text-white">Brokers</h4>
                </div>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{clusterInfo.brokerCount}</p>
              </div>

              <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-3">
                  <Database className="w-5 h-5 text-gray-500 dark:text-gray-400" />
                  <h4 className="font-medium text-gray-900 dark:text-white">Topics</h4>
                </div>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{clusterInfo.topicCount}</p>
              </div>
            </div>
          ) : (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-4 text-red-700 dark:text-red-300">
              <p className="font-medium">Failed to connect to Kafka</p>
              <p className="text-sm mt-1">
                Please ensure your Kafka cluster is running and accessible at the configured bootstrap servers.
              </p>
            </div>
          )}
        </div>

        {/* Notification Channels */}
        <NotificationChannelsSection cardClass={cardClass} inputClass={inputClass} labelClass={labelClass} />
      </div>
    </div>
  );
}

// ---- Notification Channels Section ----

const inputClass2 = 'w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-orange-500 focus:border-orange-500';
const labelClass2 = 'block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1';

const CHANNEL_ICONS: Record<string, string> = { SLACK: '💬' };

function NotificationChannelsSection({ cardClass, inputClass, labelClass }: {
  cardClass: string; inputClass: string; labelClass: string;
}) {
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<NotificationChannel | null>(null);
  const [testResults, setTestResults] = useState<Record<string, { success: boolean; message?: string; error?: string }>>({});

  const queryClient = useQueryClient();

  const { data: channels = [], isLoading } = useQuery({
    queryKey: ['notificationChannels'],
    queryFn: notificationChannelsApi.getAll,
  });

  const deleteMutation = useMutation({
    mutationFn: notificationChannelsApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notificationChannels'] }),
  });

  const testMutation = useMutation({
    mutationFn: notificationChannelsApi.test,
    onSuccess: (result, id) => setTestResults(prev => ({ ...prev, [id]: result })),
  });

  return (
    <div className={`${cardClass} p-6`}>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Notification Channels</h3>
        <button
          onClick={() => { setEditing(null); setShowModal(true); }}
          className="flex items-center gap-2 px-3 py-1.5 bg-orange-500 text-white text-sm rounded-lg hover:bg-orange-600"
        >
          <Plus className="w-4 h-4" /> Add Channel
        </button>
      </div>

      {isLoading ? (
        <div className="text-gray-500 dark:text-gray-400 py-4">Loading channels...</div>
      ) : channels.length === 0 ? (
        <div className="py-6 text-center text-gray-500 dark:text-gray-400">
          <p className="text-sm">No notification channels configured.</p>
          <p className="text-xs mt-1">Add a Slack channel to receive DLQ alerts.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {channels.map(channel => (
            <div key={channel.id} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
              <div className="flex items-center gap-3">
                <span className="text-xl">{CHANNEL_ICONS[channel.type] ?? '📣'}</span>
                <div>
                  <p className="font-medium text-gray-900 dark:text-white text-sm">{channel.name}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">{channel.type} {!channel.enabled && '· Disabled'}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {testResults[channel.id] && (
                  <span className={`text-xs ${testResults[channel.id].success ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                    {testResults[channel.id].success ? '✓ OK' : `✗ ${testResults[channel.id].error}`}
                  </span>
                )}
                <button
                  onClick={() => testMutation.mutate(channel.id)}
                  disabled={testMutation.isPending}
                  title="Send test notification"
                  className="p-1.5 text-gray-400 hover:text-blue-500 dark:hover:text-blue-400"
                >
                  {testMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                </button>
                <button
                  onClick={() => { setEditing(channel); setShowModal(true); }}
                  className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
                >
                  <Edit2 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => deleteMutation.mutate(channel.id)}
                  className="p-1.5 text-gray-400 hover:text-red-500 dark:hover:text-red-400"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <ChannelModal
          editing={editing}
          onClose={() => setShowModal(false)}
          onSaved={() => {
            setShowModal(false);
            queryClient.invalidateQueries({ queryKey: ['notificationChannels'] });
          }}
        />
      )}
    </div>
  );
}

// ---- Channel Modal ----

const CHANNEL_TYPES: NotificationChannelType[] = ['SLACK'];

const CONFIG_FIELDS: Record<NotificationChannelType, Array<{ key: string; label: string; placeholder: string; type?: string }>> = {
  SLACK: [{ key: 'webhookUrl', label: 'Webhook URL', placeholder: 'https://hooks.slack.com/services/...' }],
};

function ChannelModal({ editing, onClose, onSaved }: {
  editing: NotificationChannel | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(editing?.name ?? '');
  const [type, setType] = useState<NotificationChannelType>(editing?.type ?? 'SLACK');
  const [enabled, setEnabled] = useState(editing?.enabled ?? true);
  const [configValues, setConfigValues] = useState<Record<string, string>>(() => {
    if (editing?.configuration) {
      try { return JSON.parse(editing.configuration); } catch { return {}; }
    }
    return {};
  });
  const [error, setError] = useState('');

  const createMutation = useMutation({
    mutationFn: notificationChannelsApi.create,
    onSuccess: onSaved,
    onError: (e: Error) => setError(e.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Parameters<typeof notificationChannelsApi.update>[1] }) =>
      notificationChannelsApi.update(id, data),
    onSuccess: onSaved,
    onError: (e: Error) => setError(e.message),
  });

  const isPending = createMutation.isPending || updateMutation.isPending;

  const handleTypeChange = (newType: NotificationChannelType) => {
    setType(newType);
    setConfigValues({});
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!name.trim()) return setError('Name is required');

    const fields = CONFIG_FIELDS[type];
    const requiredField = fields.find(f => !f.label.includes('optional'));
    if (requiredField && !configValues[requiredField.key]?.trim()) {
      return setError(`${requiredField.label} is required`);
    }

    const configuration = JSON.stringify(configValues);

    if (editing) {
      updateMutation.mutate({ id: editing.id, data: { name: name.trim(), type, configuration, enabled } });
    } else {
      createMutation.mutate({ name: name.trim(), type, configuration });
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            {editing ? 'Edit Channel' : 'New Notification Channel'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={labelClass2}>Channel Name</label>
            <input type="text" value={name} onChange={e => setName(e.target.value)}
              placeholder="e.g. #alerts-slack" className={inputClass2} />
          </div>

          <div>
            <label className={labelClass2}>Type</label>
            <div className="relative">
              <select value={type} onChange={e => handleTypeChange(e.target.value as NotificationChannelType)}
                className={inputClass2 + ' appearance-none pr-8'}>
                {CHANNEL_TYPES.map(t => (
                  <option key={t} value={t}>{CHANNEL_ICONS[t]} {t}</option>
                ))}
              </select>
              <ChevronDown className="absolute right-2 top-3 w-4 h-4 text-gray-400 pointer-events-none" />
            </div>
          </div>

          {CONFIG_FIELDS[type].map(field => (
            <div key={field.key}>
              <label className={labelClass2}>{field.label}</label>
              <input
                type={field.type ?? 'text'}
                value={configValues[field.key] ?? ''}
                onChange={e => setConfigValues(prev => ({ ...prev, [field.key]: e.target.value }))}
                placeholder={field.placeholder}
                className={inputClass2}
              />
            </div>
          ))}

          {editing && (
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
              {editing ? 'Save Changes' : 'Create Channel'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
