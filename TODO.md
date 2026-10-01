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

## v4.1 — Production Hardening ✓

- [x] Browse, count and analyze messages across all partitions
- [x] Paging stays correct after retention removes old messages
- [x] Recognize Spring Kafka and Kafka Connect DLQ headers
- [x] Replayed-message tracking, duplicate replay protection, pending count
- [x] Failed replays kept in the audit trail
- [x] Replays follow Kafka settings changes without restart
- [x] Snooze silences alerts; time windows use real history
- [x] Mask Slack webhook URLs and restrict them to hooks.slack.com
- [x] Ports and credentials via environment variables
- [x] Dashboard shows live firing-alert count

---

## v5.0 — Team Access

- [x] Sign-in for the UI and API (session cookie for the browser, HTTP Basic for scripts, CSRF protection)
- [x] Role-based access control (Viewer, Operator, Admin) enforced by the API
- [ ] Single sign-on with the company identity provider (OpenID Connect)
- [x] Audit log with the real user for every action (Activity page)
- [x] Safe concurrent replays: a message being replayed by one person can't be sent again by another at the same time

---

## v6.0 — Multi-Cluster & Advanced Kafka

- [ ] Multi-cluster support with cluster switching
- [ ] Connection profiles (dev / staging / prod)
- [ ] SASL/SSL authentication support

---

## Backlog

### Message Management
- [x] Search messages by key, payload, or headers
- [x] Filter by error type or replay status
- [ ] Filter by date range
- [x] Export messages to JSON/CSV
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
- [x] Run the whole app with one `docker compose up` (backend + frontend images)
- [ ] Kubernetes Helm chart
- [ ] Prometheus metrics endpoint

### Developer Experience
- [x] Unit and integration tests
- [x] CI pipeline (GitHub Actions)
- [ ] OpenAPI / Swagger docs (`springdoc-openapi-starter-webmvc-ui`)

---

### Data Management
- [ ] Soft delete for DLQ topics, alert rules, and replay history (preserve audit trail instead of hard deleting)

---

## Known Issues

None open right now.
