# DLQ Manager - TODO

## Current Limitations

### Kafka Configuration (High Priority)
- [ ] **Kafka cluster is hardcoded** - Users must edit config files to connect to their own Kafka
- [ ] No UI to configure Kafka bootstrap servers
- [ ] No connection test before saving
- [ ] No first-time setup wizard for new users

### User Experience
- [ ] No authentication or user management
- [ ] No role-based access control
- [ ] Cannot switch between multiple Kafka clusters
- [ ] No connection profiles (dev/staging/prod)

---

## Phase 5: Alerting & Notifications (Not Started)

### Alert Rules
- [ ] Create alert rules for DLQ topics
- [ ] Threshold-based alerts (e.g., "Alert when messages > 100")
- [ ] Time-window based alerts (e.g., "Alert when 50+ messages in 5 minutes")
- [ ] Alert rule enable/disable toggle

### Notification Channels
- [ ] Slack integration
- [ ] Email notifications
- [ ] PagerDuty integration
- [ ] Webhook support for custom integrations

### Alert Management
- [ ] Alert history/log
- [ ] Acknowledge alerts
- [ ] Snooze alerts
- [ ] Alert escalation

---

## Future Enhancements

### Kafka Configuration UI
- [ ] Settings page to enter Kafka bootstrap servers
- [ ] Support for SASL/SSL authentication
- [ ] Connection test button
- [ ] Save configuration to database (not just config files)
- [ ] Multi-cluster support with cluster switching

### Message Management
- [ ] Search messages by key, payload content, or headers
- [ ] Filter messages by date range
- [ ] Filter messages by error type
- [ ] Export messages to JSON/CSV
- [ ] Archive/delete messages from DLQ
- [ ] Message preview without full modal

### Replay Enhancements
- [ ] Scheduled replays (replay at specific time)
- [ ] Replay with delay between messages
- [ ] Replay to different topic (not just source)
- [ ] Retry policies (exponential backoff)
- [ ] Dry-run mode (validate without sending)

### Analytics & Monitoring
- [ ] Message trend charts (messages over time)
- [ ] Error rate graphs
- [ ] Replay success rate trends
- [ ] Real-time message count updates
- [ ] Kafka consumer lag monitoring

### Security
- [ ] User authentication (username/password or OAuth)
- [ ] Role-based access control (Admin, Viewer, Operator)
- [ ] Audit log for all actions
- [ ] API key authentication for programmatic access

### Deployment
- [ ] Single Docker image with frontend + backend
- [ ] Kubernetes Helm chart
- [ ] Docker Compose for production
- [ ] Health check endpoints
- [ ] Prometheus metrics endpoint

### Developer Experience
- [ ] Unit tests for backend services
- [ ] Integration tests with embedded Kafka
- [ ] Frontend component tests
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] API documentation (Swagger/OpenAPI)

---

## Known Issues

- [ ] Error breakdown shows "Unknown Error" for messages without X-Error-Message header
- [ ] Pagination starts from offset 0, may miss messages if topic has retention policy
- [ ] No handling for Kafka connection failures in UI (just shows loading)

---

## Quick Wins (Easy to Implement)

- [ ] Add "Copy Topic Name" button
- [ ] Add message count badge on topic cards
- [ ] Show last message timestamp on topic list
- [ ] Add keyboard shortcuts (R for refresh, etc.)
- [ ] Dark mode toggle
- [ ] Remember last visited page

---

## Contributing

Feel free to pick any item from this list and submit a PR. For major features, please open an issue first to discuss the approach.
