import { Header } from '../components/layout';
import { Bell, Slack, Mail, AlertTriangle } from 'lucide-react';

export function AlertsPage() {
  return (
    <div className="min-h-screen">
      <Header
        title="Alerts"
        subtitle="Configure alerting rules for DLQ monitoring"
      />

      <div className="p-6">
        {/* Coming Soon Banner */}
        <div className="bg-gradient-to-r from-orange-500 to-orange-600 rounded-lg p-8 text-white mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 bg-white bg-opacity-20 rounded-lg">
              <Bell className="w-8 h-8" />
            </div>
            <div>
              <h2 className="text-2xl font-bold">Phase 4: Alerting</h2>
              <p className="text-orange-100 mt-1">
                Configure alerts for DLQ message thresholds and get notified via Slack, Email, or PagerDuty.
              </p>
            </div>
          </div>
        </div>

        {/* Planned Features */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
          <FeatureCard
            icon={AlertTriangle}
            title="Threshold Alerts"
            description="Get notified when DLQ message count exceeds a configurable threshold within a time window."
            status="Coming Soon"
          />
          <FeatureCard
            icon={Slack}
            title="Slack Integration"
            description="Send alerts to Slack channels with rich message formatting and action buttons."
            status="Coming Soon"
          />
          <FeatureCard
            icon={Mail}
            title="Email Notifications"
            description="Configure email alerts for your team with customizable templates."
            status="Coming Soon"
          />
        </div>

        {/* Placeholder UI */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200">
          <div className="px-6 py-4 border-b border-gray-200">
            <h3 className="text-lg font-semibold text-gray-900">Alert Rules</h3>
          </div>
          <div className="p-8 text-center">
            <Bell className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <h4 className="text-lg font-medium text-gray-900 mb-2">No alert rules configured</h4>
            <p className="text-gray-500 mb-4">
              Alert configuration will be available in Phase 4.
            </p>
            <button
              disabled
              className="px-4 py-2 bg-orange-100 text-orange-400 rounded-lg cursor-not-allowed"
            >
              + Add Alert Rule
            </button>
          </div>
        </div>

        {/* Configuration Preview */}
        <div className="mt-8 bg-gray-50 rounded-lg border border-gray-200 p-6">
          <h4 className="text-sm font-semibold text-gray-700 mb-4">Preview: Alert Rule Configuration</h4>
          <div className="bg-white rounded-lg border border-gray-200 p-4 opacity-60">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">DLQ Topic</label>
                <select disabled className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50">
                  <option>orders-dlq</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Threshold</label>
                <input
                  type="number"
                  disabled
                  value="50"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Time Window</label>
                <select disabled className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50">
                  <option>1 hour</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notification Channel</label>
                <select disabled className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50">
                  <option>Slack #alerts</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

interface FeatureCardProps {
  icon: React.ElementType;
  title: string;
  description: string;
  status: string;
}

function FeatureCard({ icon: Icon, title, description, status }: FeatureCardProps) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
      <div className="flex items-start justify-between mb-4">
        <div className="p-2 bg-orange-100 rounded-lg">
          <Icon className="w-6 h-6 text-orange-600" />
        </div>
        <span className="text-xs font-medium text-orange-600 bg-orange-100 px-2 py-1 rounded-full">
          {status}
        </span>
      </div>
      <h4 className="text-lg font-semibold text-gray-900 mb-2">{title}</h4>
      <p className="text-gray-500 text-sm">{description}</p>
    </div>
  );
}
