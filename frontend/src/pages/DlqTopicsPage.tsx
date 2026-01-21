import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Header } from '../components/layout';
import { dlqTopicsApi } from '../api/dlqTopics';
import { kafkaApi } from '../api/kafka';
import type { DlqTopic } from '../types';
import {
  Plus,
  Trash2,
  Edit2,
  Eye,
  Search,
  X
} from 'lucide-react';
import { Link } from 'react-router-dom';

export function DlqTopicsPage() {
  const queryClient = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [editingTopic, setEditingTopic] = useState<DlqTopic | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  const { data: dlqTopics, isLoading, refetch } = useQuery({
    queryKey: ['dlqTopics'],
    queryFn: dlqTopicsApi.getAll,
  });

  const { data: kafkaTopics } = useQuery({
    queryKey: ['kafkaTopics'],
    queryFn: kafkaApi.getTopics,
  });

  const deleteMutation = useMutation({
    mutationFn: dlqTopicsApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dlqTopics'] });
    },
  });

  const filteredTopics = dlqTopics?.filter(topic =>
    topic.dlqTopicName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    topic.sourceTopic.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleDelete = async (id: string, name: string) => {
    if (window.confirm(`Are you sure you want to delete "${name}"?`)) {
      await deleteMutation.mutateAsync(id);
    }
  };

  const handleEdit = (topic: DlqTopic) => {
    setEditingTopic(topic);
    setShowModal(true);
  };

  return (
    <div className="min-h-screen">
      <Header
        title="DLQ Topics"
        subtitle="Manage your Dead Letter Queue topics"
        onRefresh={() => refetch()}
        isRefreshing={isLoading}
      />

      <div className="p-6">
        {/* Actions Bar */}
        <div className="flex items-center justify-between mb-6">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search topics..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500 w-64"
            />
          </div>
          <button
            onClick={() => {
              setEditingTopic(null);
              setShowModal(true);
            }}
            className="flex items-center gap-2 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Add DLQ Topic
          </button>
        </div>

        {/* Topics Table */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="overflow-x-auto">
            {isLoading ? (
              <div className="p-8 text-center text-gray-500">Loading...</div>
            ) : filteredTopics && filteredTopics.length > 0 ? (
              <table className="w-full">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">DLQ Topic</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Source Topic</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Detection</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {filteredTopics.map((topic) => (
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
                      <td className="px-6 py-4 whitespace-nowrap text-gray-500 text-sm">
                        {new Date(topic.createdAt).toLocaleDateString()}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            to={`/dlq-topics/${topic.id}`}
                            className="p-2 text-gray-500 hover:text-orange-600 hover:bg-orange-50 rounded-lg transition-colors"
                            title="View Messages"
                          >
                            <Eye className="w-4 h-4" />
                          </Link>
                          <button
                            onClick={() => handleEdit(topic)}
                            className="p-2 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                            title="Edit"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDelete(topic.id, topic.dlqTopicName)}
                            className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                            title="Delete"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-gray-500">
                {searchTerm ? 'No topics match your search.' : 'No DLQ topics registered yet.'}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Add/Edit Modal */}
      {showModal && (
        <DlqTopicModal
          topic={editingTopic}
          kafkaTopics={kafkaTopics?.map(t => t.name) || []}
          onClose={() => {
            setShowModal(false);
            setEditingTopic(null);
          }}
          onSuccess={() => {
            setShowModal(false);
            setEditingTopic(null);
            queryClient.invalidateQueries({ queryKey: ['dlqTopics'] });
          }}
        />
      )}
    </div>
  );
}

interface DlqTopicModalProps {
  topic: DlqTopic | null;
  kafkaTopics: string[];
  onClose: () => void;
  onSuccess: () => void;
}

function DlqTopicModal({ topic, kafkaTopics, onClose, onSuccess }: DlqTopicModalProps) {
  const [formData, setFormData] = useState({
    dlqTopicName: topic?.dlqTopicName || '',
    sourceTopic: topic?.sourceTopic || '',
    detectionType: topic?.detectionType || 'MANUAL',
    errorFieldPath: topic?.errorFieldPath || 'headers.X-Error-Message',
    status: topic?.status || 'ACTIVE',
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setError('');

    try {
      if (topic) {
        await dlqTopicsApi.update(topic.id, formData);
      } else {
        await dlqTopicsApi.create(formData);
      }
      onSuccess();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } }; message?: string };
      setError(error.response?.data?.message || error.message || 'An error occurred');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-md mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {topic ? 'Edit DLQ Topic' : 'Add DLQ Topic'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              DLQ Topic Name *
            </label>
            <select
              value={formData.dlqTopicName}
              onChange={(e) => setFormData({ ...formData, dlqTopicName: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
              required
              disabled={!!topic}
            >
              <option value="">Select a topic</option>
              {kafkaTopics.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Source Topic *
            </label>
            <select
              value={formData.sourceTopic}
              onChange={(e) => setFormData({ ...formData, sourceTopic: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
              required
            >
              <option value="">Select source topic</option>
              {kafkaTopics.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Error Field Path
            </label>
            <input
              type="text"
              value={formData.errorFieldPath}
              onChange={(e) => setFormData({ ...formData, errorFieldPath: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
              placeholder="headers.X-Error-Message"
            />
            <p className="mt-1 text-xs text-gray-500">JSON path to extract error message</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Status
            </label>
            <select
              value={formData.status}
              onChange={(e) => setFormData({ ...formData, status: e.target.value as 'ACTIVE' | 'PAUSED' })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-orange-500 focus:border-orange-500"
            >
              <option value="ACTIVE">Active</option>
              <option value="PAUSED">Paused</option>
            </select>
          </div>

          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="flex-1 px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors disabled:opacity-50"
            >
              {isSubmitting ? 'Saving...' : topic ? 'Update' : 'Add Topic'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
