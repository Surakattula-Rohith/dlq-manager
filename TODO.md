# DLQ Manager — Backlog

---

## v4.0 — Alerting & Notifications ✓

- [x] Threshold-based alert rules (fire when message count ≥ N)
- [x] Time-window alert rules (fire when count increases by ≥ N in X minutes)
- [x] Alert rule enable/disable toggle
- [x] Cooldown support to prevent duplicate alerts
- [x] Alert history with Firing / Acknowledged / Snoozed states
- [x] Acknowledge and snooze actions
- [x] Slack notifications via incoming webhooks

---

## v5.0 — Authentication & RBAC

- [ ] User authentication (username/password or OAuth)
- [ ] Role-based access control (Admin, Viewer, Operator)
- [ ] Audit log for all user actions
- [ ] API key authentication for programmatic access

---

## v6.0 — Multi-Cluster & Advanced Kafka

- [ ] Multi-cluster support with cluster switching
- [ ] Connection profiles (dev / staging / prod)
- [ ] SASL/SSL authentication support

---

## Backlog

### Message Management
- [ ] Search messages by key, payload, or headers
- [ ] Filter by date range or error type
- [ ] Export messages to JSON/CSV
- [ ] Archive/delete messages from DLQ

### Replay Enhancements
- [ ] Scheduled replays
- [ ] Replay to a different topic
- [ ] Dry-run mode (validate without sending)

### Analytics
- [ ] Message trend charts over time
- [ ] Real-time message count updates
- [ ] Kafka consumer lag monitoring

### Deployment
- [ ] Single Docker image (frontend + backend)
- [ ] Kubernetes Helm chart
- [ ] Prometheus metrics endpoint

### Developer Experience
- [ ] Unit and integration tests
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] OpenAPI / Swagger docs (`springdoc-openapi-starter-webmvc-ui`)

---

## Known Issues

| Issue | Priority |
|-------|----------|
| Error breakdown shows "Unknown Error" for messages without `X-Error-Message` header | Low |
| Pagination may miss messages near retention boundary | Low |
