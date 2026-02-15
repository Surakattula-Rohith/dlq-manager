# DLQ Manager — Backlog & Roadmap

---

## v1.0 — Core Platform (Completed)

| Feature | Status |
|---------|--------|
| DLQ Topic Management (CRUD) | Done |
| Message Browsing with pagination | Done |
| Error Analytics & breakdown | Done |
| Single & Bulk Message Replay | Done |
| Replay History & audit trail | Done |
| Auto-Discovery of DLQ topics | Done |
| React + Tailwind Frontend | Done |
| Dashboard with overview cards | Done |

---

## v2.0 — Dynamic Kafka Configuration (Completed)

| Feature | Status |
|---------|--------|
| Settings page to configure bootstrap servers | Done |
| Connection test button (must pass before save) | Done |
| Save disabled until test succeeds | Done |
| Configuration stored in PostgreSQL | Done |
| Hot reload — no backend restart needed | Done |
| Fallback to `application.properties` if no DB config | Done |
| First-time setup banner for new users | Done |

---

## v3.0 — Alerting & Notifications (Planned)

### Alert Rules
- [ ] Create alert rules for DLQ topics
- [ ] Threshold-based alerts (e.g., "Alert when messages > 100")
- [ ] Time-window alerts (e.g., "50+ messages in 5 minutes")
- [ ] Alert rule enable/disable toggle

### Notification Channels
- [ ] Slack integration
- [ ] Email notifications
- [ ] PagerDuty integration
- [ ] Webhook support for custom integrations

### Alert Management
- [ ] Alert history/log
- [ ] Acknowledge & snooze alerts
- [ ] Alert escalation

---

## v4.0 — Authentication & RBAC (Planned)

- [ ] User authentication (username/password or OAuth)
- [ ] Role-based access control (Admin, Viewer, Operator)
- [ ] Audit log for all user actions
- [ ] API key authentication for programmatic access

---

## v5.0 — Multi-Cluster & Advanced Kafka (Planned)

- [ ] Multi-cluster support with cluster switching
- [ ] Connection profiles (dev/staging/prod)
- [ ] SASL/SSL authentication support

---

## Future Enhancements

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

| Issue | Priority |
|-------|----------|
| Error breakdown shows "Unknown Error" for messages without `X-Error-Message` header | Low |
| Pagination starts from offset 0, may miss messages with retention policy | Low |

---

## Quick Wins

- [ ] Add "Copy Topic Name" button
- [ ] Add message count badge on topic cards
- [ ] Show last message timestamp on topic list
- [ ] Add keyboard shortcuts (R for refresh, etc.)
- [ ] Dark mode toggle
- [ ] Remember last visited page

---

## Contributing

Feel free to pick any item from this list and submit a PR. For major features, please open an issue first to discuss the approach.
