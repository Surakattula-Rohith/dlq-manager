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
}

export interface MessagePage {
  messages: DlqMessage[];
  currentPage: number;
  totalPages: number;
  totalMessages: number;
  pageSize: number;
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

export interface ReplayRequest {
  dlqTopicId: string;
  offset: number;
  partition: number;
  initiatedBy: string;
}

export interface BulkReplayRequest {
  dlqTopicId: string;
  messages: { offset: number; partition: number }[];
  initiatedBy: string;
}

// Alert types
export interface AlertRule {
  id: string;
  dlqTopicId?: string;
  dlqTopicName?: string;
  threshold: number;
  windowMinutes: number;
  notificationChannel: 'SLACK' | 'EMAIL' | 'PAGERDUTY';
  enabled: boolean;
  createdAt: string;
}

export interface AlertEvent {
  id: string;
  alertRuleId: string;
  dlqTopicName: string;
  status: 'FIRING' | 'RESOLVED';
  messageCount: number;
  triggeredAt: string;
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
