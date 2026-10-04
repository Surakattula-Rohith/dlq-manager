// DLQ Topic types
export interface DlqTopic {
  id: string;
  dlqTopicName: string;
  sourceTopic: string;
  detectionType: 'AUTO' | 'MANUAL';
  errorFieldPath?: string;
  status: 'ACTIVE' | 'PAUSED';
  createdAt: string;
  updatedAt: string;
}

// Message types
export interface DlqMessage {
  offset: number;
  partition: number;
  key?: string;
  payload: string;
  timestamp: string;
  headers: Record<string, string>;
  errorMessage?: string;
  originalTopic?: string;
  retryCount?: number;
  exceptionClass?: string;
  failedTimestamp?: string;
  replayed?: boolean;
  replayedAt?: string;
}

export interface MessagePage {
  messages: DlqMessage[];
  currentPage: number;
  totalPages: number;
  totalMessages: number;
  pendingMessages: number;
  replayedMessages: number;
  matchingMessages: number;
  filtered: boolean;
  scanLimitReached: boolean;
  pageSize: number;
}

export interface MessageFilters {
  search?: string;
  errorType?: string;
  pendingOnly?: boolean;
}

// Error breakdown types
export interface ErrorBreakdownItem {
  errorType: string;
  count: number;
  percentage: number;
}

export interface ErrorBreakdown {
  success: boolean;
  totalMessages: number;
  uniqueErrorTypes: number;
  errorBreakdown: ErrorBreakdownItem[];
}

// Replay types
export interface ReplayJob {
  id: string;
  dlqTopicId: string;
  dlqTopicName?: string;
  sourceTopic?: string;
  initiatedBy: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'PARTIALLY_COMPLETED';
  totalMessages: number;
  succeeded: number;
  failed: number;
  successRate?: number;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

// Replays are recorded under the signed-in user (set by the server)
export interface ReplayRequest {
  dlqTopicId: string;
  offset: number;
  partition: number;
}

export interface BulkReplayRequest {
  dlqTopicId: string;
  messages: { offset: number; partition: number }[];
  force?: boolean;
}

// Alert types
export type AlertType = 'THRESHOLD' | 'TIME_WINDOW';
export type AlertStatus = 'FIRING' | 'ACKNOWLEDGED' | 'SNOOZED';
export type NotificationChannelType = 'SLACK';

// Kinds of team activity a Slack channel can follow (the "team feed")
export type ActivityCategory = 'REPLAYS' | 'ALERTS' | 'CHANGES';

export interface NotificationChannel {
  id: string;
  name: string;
  type: NotificationChannelType;
  configuration: string; // JSON string
  enabled: boolean;
  activityFeed: ActivityCategory[]; // empty = the channel only receives DLQ alerts
  createdAt: string;
  updatedAt: string;
}

export interface AlertRule {
  id: string;
  name: string;
  dlqTopicId: string;
  dlqTopicName: string;
  alertType: AlertType;
  threshold: number;
  windowMinutes?: number;
  notificationChannelId?: string;
  notificationChannelName?: string;
  notificationChannelType?: NotificationChannelType;
  cooldownMinutes: number;
  enabled: boolean;
  lastFiredAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AlertEvent {
  id: string;
  alertRuleId: string;
  alertRuleName: string;
  dlqTopicName: string;
  status: AlertStatus;
  messageCount: number;
  triggeredAt: string;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  snoozedUntil?: string;
  snoozedBy?: string;
}

// Dashboard types
export interface DashboardSummary {
  totalDlqTopics: number;
  totalMessages: number;
  messagesLast24h: number;
  activeAlerts: number;
}

export interface DlqMetric {
  dlqTopicId: string;
  dlqTopicName: string;
  messageCount: number;
  newMessages: number;
  topError?: string;
  topErrorPercentage?: number;
}

// API Response wrapper
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: string;
}

// Sign-in
export type Role = 'VIEWER' | 'OPERATOR' | 'ADMIN';

export interface AuthSession {
  authenticated: boolean;
  username?: string;
  role?: Role;
  demoAccounts: boolean;
}

// Activity log
export type ActivityAction =
  | 'SIGNED_IN' | 'SIGNED_OUT' | 'SIGN_IN_FAILED'
  | 'MESSAGES_REPLAYED'
  | 'ALERT_ACKNOWLEDGED' | 'ALERT_SNOOZED'
  | 'DLQ_TOPIC_ADDED' | 'DLQ_TOPIC_UPDATED' | 'DLQ_TOPIC_DELETED'
  | 'ALERT_RULE_CREATED' | 'ALERT_RULE_UPDATED' | 'ALERT_RULE_ENABLED' | 'ALERT_RULE_DISABLED' | 'ALERT_RULE_DELETED'
  | 'CHANNEL_CREATED' | 'CHANNEL_UPDATED' | 'CHANNEL_DELETED'
  | 'KAFKA_SETTINGS_CHANGED';

export interface ActivityEntry {
  id: string;
  occurredAt: string;
  username: string;
  action: ActivityAction;
  target?: string;
  details?: string;
}

export interface ActivityPage {
  activity: ActivityEntry[];
  pagination: { currentPage: number; pageSize: number; totalItems: number; totalPages: number };
  usernames: string[];
}
