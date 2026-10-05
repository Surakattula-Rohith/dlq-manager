# Kafka DLQ Manager

[![CI](https://github.com/Surakattula-Rohith/dlq-manager/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Surakattula-Rohith/dlq-manager/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![React](https://img.shields.io/badge/React-19-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6)
![Tailwind CSS](https://img.shields.io/badge/Tailwind-4.x-06B6D4)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-orange)
![License](https://img.shields.io/badge/License-AGPL--3.0-purple)

A full-stack dashboard for managing Kafka Dead Letter Queues — browse failed messages, analyze error patterns, replay them back to source topics, and get alerted when things go wrong.

> A quick tour: sign in as an operator, filter the failed messages by error type, replay two of them, and see the replay in the activity log.

![Demo](assets/Animation.gif)

---

## Why I built this

When Kafka consumers fail, messages land in a DLQ and stay there — invisible, untracked, silently growing. Most teams deal with this by writing one-off scripts or digging through CLI tools. I wanted a proper UI for it: something that shows you what failed, why it failed, and lets you fix it without leaving the browser.

This project started as a weekend experiment and has grown into a full platform over 4 weekly iterations, each shipped as a LinkedIn update.

---

## What it does

- **Browse** failed messages across all your DLQ topics, paginated — search them, filter by error type or by when they failed, or click a period on the trend chart to list its messages
- **Analyze** error patterns with a per-topic error breakdown, and see whether a DLQ is growing or shrinking on a 24-hour / 7-day trend chart
- **Replay** single or bulk messages back to the source topic, and see first whether the services reading that topic are running and keeping up (consumer lag) — with a warning before replaying into one that isn't
- **Track** every replay operation with a full audit trail
- **Configure** Kafka connections from the UI — no restart needed, including clusters that need a login and encryption (SASL + TLS)
- **Alert** when a DLQ crosses a threshold, with Slack notifications
- **Share** with a team: sign-in with viewer, operator and admin roles
- **Audit** who did what on the Activity page: replays, alert actions, sign-ins and every configuration change
- **Follow** the team in Slack: replays, alert actions and setup changes are posted to the channels that opt in

---

## Build History

Each version was shipped and documented independently.

**v1.0 — Core Platform**
The foundation: DLQ topic management, a message browser with pagination, error analytics, single and bulk replay, and a full replay audit trail. Auto-discovery detects DLQ topics by naming convention (`*-dlq`, `*-error`).

**v2.0 — Dynamic Kafka Configuration**
Added a Settings page to configure bootstrap servers from the UI, with a connection test that must pass before saving. Config is stored in PostgreSQL and takes effect immediately — no backend restart.

**v3.0 — Dark Mode**
Full dark mode across every page, input, table, modal, and card. Persisted across sessions.

**v4.0 — Alerting & Notifications**
Threshold and time-window alert rules per DLQ topic. Alert history with Acknowledge and Snooze actions. Cooldown support to prevent alert spam. Slack notifications via incoming webhooks.

**v4.1 — Production Hardening**
Made the tool behave correctly on real Kafka setups, not just demo data:
- Reads **every partition** of a DLQ, and paging stays correct after Kafka retention removes old messages
- Understands the DLQ headers written by **Spring Kafka** (`kafka_dlt-*`) and **Kafka Connect** (`__connect.errors.*`), plus an optional JSON error field
- Tracks which messages were already **replayed** — shown with a badge, blocked from accidental duplicate replays, and excluded from the new **pending** count
- Failed replays are always kept in the audit trail; bulk replay reuses a single consumer
- Replays follow Kafka settings changes without a restart
- Alerts: snooze actually silences, time windows measure the real window, and threshold alerts use the pending count
- Slack webhook URLs are masked in the API and only `hooks.slack.com` addresses are accepted
- Ports and credentials configurable through environment variables

**v5.0 — Team Access**
Turned a single-user tool into one a team can share on a server:
- **Sign-in and roles** — viewer, operator and admin, enforced by the API; session cookie with CSRF protection for the browser, HTTP Basic for scripts
- **Activity page** — every replay, alert action, sign-in and configuration change is recorded under the real user
- **Safe concurrent replays** — when two people replay the same message at the same moment, only one replay is sent
- **Faster under shared use** — Kafka connections are reused (a DLQ page loads about 8x faster) and one error-breakdown scan is shared by everyone viewing the topic
- **Secured Kafka** — SASL login (PLAIN, SCRAM) and TLS with an optional company certificate; the Kafka password is stored encrypted and never sent back to the browser
- **Team feed in Slack** — channels can follow replays, alert actions and setup changes as they happen

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 19, TypeScript, Vite 7, Tailwind CSS 4 |
| State | TanStack Query, React Router, Axios |
| Backend | Java 21, Spring Boot 3.5, Spring Data JPA |
| Messaging | Apache Kafka Client (consumer + producer) |
| Database | PostgreSQL 15 |
| Infra | Docker Compose, Kafka in KRaft mode (no ZooKeeper) |

---

## Quick Start

### Run everything with Docker

**Prerequisites:** Docker

```bash
git clone https://github.com/Surakattula-Rohith/dlq-manager.git
cd dlq-manager
docker compose up --build
```

Open **http://localhost:3000** and sign in with a demo account (`admin` / `admin`, `operator` / `operator` or `viewer` / `viewer`). The UI, API, Kafka and PostgreSQL all run in containers, so no Java or Node is needed. The backend is already pointed at Kafka (`kafka:29092` inside Docker).

### Run from source (development)

**Prerequisites:** Docker, Java 21+, Node.js 20.19+

```bash
# 1. Start infrastructure only (Kafka + PostgreSQL)
docker compose up -d kafka postgres

# 2. Start backend
cd backend && ./mvnw spring-boot:run      # http://localhost:8080

# 3. Start frontend (in a second terminal, from the project root)
cd frontend && npm install && npm run dev  # http://localhost:5173
```

Kafka is reachable at `localhost:9092` from your machine (the default in **Settings**).

**Ports already in use?** Every default can be changed with an environment variable:

```bash
FRONTEND_PORT=3001 POSTGRES_PORT=5433 docker compose up --build     # Docker
cd backend && SERVER_PORT=8081 DB_URL='jdbc:postgresql://localhost:5433/dlqmanager' ./mvnw spring-boot:run
cd frontend && FRONTEND_PORT=5174 BACKEND_URL=http://localhost:8081 npm run dev
```

Backend variables: `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `SHOW_SQL`, `LOG_LEVEL`, `ERROR_BREAKDOWN_CACHE_TTL`.

The error breakdown reads every message in a DLQ, so its result is remembered for 30 seconds (`ERROR_BREAKDOWN_CACHE_TTL`, e.g. `2m`, or `0s` to turn it off) and shared by everyone viewing that topic. Message counts are always live.

### Sign-in

Every page and API call needs a signed-in user. Three demo accounts exist out of the box, with the password equal to the username: `admin`, `operator` and `viewer`.

| Role | Can do |
|------|--------|
| Viewer | Browse, search and export messages; see replay history, alerts and settings |
| Operator | Everything a viewer can, plus replay messages and acknowledge or snooze alerts |
| Admin | Everything, including DLQ topics, alert rules, Slack channels and Kafka settings |

Roles are enforced by the API (a forbidden call returns `403`); the UI also hides actions your role can't use. If two people replay the same message at the same moment, only one replay sends it — each message is claimed in the database before it is sent, which also holds with several backend instances. Replays, acknowledged alerts and snoozed alerts are recorded under the signed-in user, and replayed messages carry it in the `X-Replayed-By` header. The **Activity** page lists everything people did — replays, alert actions, sign-ins (including failed ones) and changes to DLQ topics, alert rules, Slack channels and Kafka settings — filterable by person and action.

**Before sharing the app with a team**, change the passwords:

```bash
DLQ_ADMIN_PASSWORD=... DLQ_OPERATOR_PASSWORD=... DLQ_VIEWER_PASSWORD=... docker compose up -d
```

To use your own accounts instead, set `DLQ_AUTH_USERS_0_USERNAME`, `DLQ_AUTH_USERS_0_PASSWORD`, `DLQ_AUTH_USERS_0_ROLE` (`VIEWER`, `OPERATOR` or `ADMIN`), then `DLQ_AUTH_USERS_1_...` and so on. This replaces the demo accounts. Passwords can also be given as a bcrypt hash (`{bcrypt}$2a$10$...`).

The web UI keeps you signed in with a session cookie (8 hours, `SESSION_TIMEOUT`). Set `SESSION_COOKIE_SECURE=true` when the app is served over HTTPS. Scripts can call the API with HTTP Basic instead:

```bash
curl -u viewer:viewer http://localhost:3000/api/dlq-topics     # :8080 when running from source
```

### Team feed in Slack

A Slack channel added under **Settings → Notification Channels** can also follow what people do, so the team sees it without opening the app. Each channel chooses what it follows:

| Follows | Posted when someone | Example |
|---------|---------------------|---------|
| Replays | replays messages | `operator replayed messages from orders-dlq - 12 message(s): 12 succeeded, 0 failed` |
| Alert actions | acknowledges or snoozes an alert | `operator snoozed the alert Payments backlog - payments-dlq, for 60 min` |
| Setup changes | changes DLQ topics, alert rules, channels or the Kafka connection | `admin added DLQ topic payments-dlq - source: payments, status: ACTIVE` |

Posts are sent in the background, in the order things happened: a slow or unreachable Slack never delays or fails a replay (the Activity page keeps the full record either way). Sign-ins are never posted, and names typed by people can't trigger `@channel` mentions or links.

### Connecting to a secured Kafka

A company cluster usually needs an encrypted connection and a login. An admin sets both under **Settings → Security**, and the connection test has to pass before it can be saved:

| Login | Typically used by |
|-------|-------------------|
| No login | Local development |
| Username + password (SCRAM-SHA-512 / SCRAM-SHA-256) | AWS MSK, Aiven, Redpanda, most self-hosted clusters |
| Username + password (PLAIN) | Confluent Cloud (API key and secret) |

- **Encrypted connection (TLS)** — one checkbox. If the brokers use a certificate from a company-internal authority, paste that authority's certificate (PEM text) into the box below it; no keystore files are needed.
- **The Kafka password** is stored encrypted (AES-256-GCM). The key comes from `DLQ_SECRET_KEY`, which the backend needs to be started with — without it the app refuses to store a password rather than keep it as plain text. The password is never sent back to the browser, never logged, and only reused for the same brokers and username it was saved for.
- When a test fails, the message says what to fix: wrong password, untrusted certificate, or brokers that expect a login or encryption that wasn't used.

```bash
DLQ_SECRET_KEY='any long random text' docker compose up -d     # keep the same key between restarts
```

To keep the Kafka password out of the app's database entirely, give the connection to the backend as environment variables instead (used until something is saved from Settings): `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_AUTHENTICATION` (`NONE`, `PLAIN`, `SCRAM_SHA_256`, `SCRAM_SHA_512`), `KAFKA_ENCRYPTED`, `KAFKA_USERNAME`, `KAFKA_PASSWORD`, `KAFKA_CA_CERTIFICATE`.

Not supported yet: client certificates (mTLS), Kerberos and OAuth logins.

---

## Running Tests

```bash
cd backend && ./mvnw test
```

- **Unit tests** — header parsing, message conversion, alert rules, webhook safety (no infrastructure needed)
- **Integration tests** — run against real PostgreSQL and Kafka started automatically with [Testcontainers](https://testcontainers.com) (Docker must be running): multi-partition paging, paging after retention, replay, duplicate-replay protection, sign-in and CSRF protection. One test starts a second Kafka that only accepts TLS plus a login, and browses and replays through it

CI runs the full backend suite plus frontend lint and build on every push.

---

## Screenshots

<details>
<summary><strong>Dashboard & Topics</strong></summary>

![Dashboard](assets/01-dashboard.png)
![Dashboard Dark](assets/15-dark-dashboard.png)
![DLQ Topics](assets/02-dlq-topics-list.png)
![DLQ Topics Dark](assets/16-dark-dlq-topics.png)

</details>

<details>
<summary><strong>Message Browser, Search & Replay</strong></summary>

![Message Browser with Error Breakdown](assets/04-topic-detail-error-breakdown.png)
![Trend: pending messages and new failures per hour](assets/33-topic-trend.png)
![Trend over 7 days (Dark)](assets/34-dark-topic-trend.png)
![Where replays go: consumers of the source topic and their lag](assets/35-source-consumers.png)
![Messages from one hour, picked by clicking the trend chart](assets/36-time-filter.png)
![Filtered by error type, replayed messages hidden](assets/27-search-filter.png)
![Message Detail](assets/05-message-detail-modal.png)
![Message Browser Dark](assets/31-dark-topic-detail.png)
![Replay History](assets/06-replay-history.png)
![Replay History Dark](assets/17-dark-replay-history.png)

</details>

<details>
<summary><strong>Sign-in, Roles & Activity</strong></summary>

![Sign-in](assets/00-login.png)
![Viewer: read-only message browser](assets/28-viewer-read-only.png)
![Activity log](assets/29-activity.png)
![Activity log Dark](assets/30-dark-activity.png)

</details>

<details>
<summary><strong>Alerts & Slack Notifications</strong></summary>

![Alert Rules](assets/20-alerts-rules.png)
![Alert History](assets/24-alert-history.png)
![Alert History - Snoozed](assets/25-alert-history-snoozed.png)
![Alerts Dark](assets/18-dark-alerts.png)
![Snooze Modal Dark](assets/26-dark-alert-snooze-modal.png)
![Slack Notification](assets/22-slack-notification.png)
![Slack Multiple Alerts](assets/23-slack-multiple-alerts.png)
![Slack Team Feed Settings](assets/32-slack-team-feed.png)

</details>

<details>
<summary><strong>Settings</strong></summary>

![Settings](assets/10-settings-kafka-config.png)
![Secured Kafka: Connection Test Success](assets/11-settings-connection-test-success.png)
![Secured Kafka: Connection Test Failed](assets/13-settings-connection-test-failed.png)
![Secured Kafka Saved (Dark)](assets/19-dark-settings.png)
![Add DLQ Topic](assets/09-add-topic-modal.png)

</details>

---

## API Reference

<details>
<summary><strong>Endpoints</strong></summary>

### Sign-in
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Sign in (form fields `username`, `password`) and start a session |
| `POST` | `/api/auth/logout` | Sign out |
| `GET` | `/api/auth/me` | Who is signed in |
| `GET` | `/api/activity` | Activity log, newest first (optional `username`, `action`, `page`, `size`) |

All other endpoints need a session (the web UI) or HTTP Basic credentials (scripts). `GET` endpoints are open to every role; replay and alert acknowledge/snooze need `OPERATOR`; everything else needs `ADMIN`.

### DLQ Topics
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/dlq-topics` | List all registered DLQs |
| `POST` | `/api/dlq-topics` | Register new DLQ topic |
| `PUT` | `/api/dlq-topics/{id}` | Update DLQ configuration |
| `DELETE` | `/api/dlq-topics/{id}` | Delete DLQ registration |
| `GET` | `/api/dlq-topics/{id}/messages` | Browse messages (paginated; optional `search`, `errorType`, `pendingOnly`, `from`, `to` filters) |
| `GET` | `/api/dlq-topics/{id}/messages/export` | Download messages as `format=csv` or `format=json` (same filters) |
| `GET` | `/api/dlq-topics/{id}/error-breakdown` | Error type statistics |
| `GET` | `/api/dlq-topics/{id}/trend` | Pending messages and new failures over time (`range=24h` or `7d`) |
| `GET` | `/api/dlq-topics/{id}/source-consumers` | Consumer groups of the source topic: running or not, and their lag |

### Replay
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/replay/single` | Replay single message |
| `POST` | `/api/replay/bulk` | Replay multiple messages |
| `GET` | `/api/replay/history` | All replay jobs |

### Kafka & Configuration
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/kafka/cluster-info` | Cluster information |
| `GET` | `/api/kafka/discover-dlqs` | Auto-discover DLQ topics |
| `GET` | `/api/kafka/config` | Get current connection settings (never the password) |
| `PUT` | `/api/kafka/config` | Save brokers, login and encryption settings |
| `POST` | `/api/kafka/config/test` | Test a connection without saving it |

### Alerts
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/alert-rules` | List alert rules |
| `POST` | `/api/alert-rules` | Create alert rule |
| `PUT` | `/api/alert-rules/{id}` | Update alert rule |
| `PATCH` | `/api/alert-rules/{id}/toggle` | Enable/disable rule |
| `DELETE` | `/api/alert-rules/{id}` | Delete rule |
| `GET` | `/api/alert-events` | Alert history |
| `POST` | `/api/alert-events/{id}/acknowledge` | Acknowledge alert |
| `POST` | `/api/alert-events/{id}/snooze` | Snooze alert |
| `GET` | `/api/notification-channels` | List channels |
| `POST` | `/api/notification-channels` | Create channel (`activityFeed`: `REPLAYS`, `ALERTS`, `CHANGES` to also post team activity) |
| `PUT` | `/api/notification-channels/{id}` | Update channel |
| `POST` | `/api/notification-channels/{id}/test` | Test channel |

</details>

---

## Roadmap

- [x] v1.0 — DLQ browsing, error analytics, message replay
- [x] v2.0 — Dynamic Kafka configuration from UI
- [x] v3.0 — Dark mode
- [x] v4.0 — Alerting with Slack notifications
- [x] v4.1 — Production hardening (partitions, real-world headers, replay safety, alert fixes)
- [x] v5.0 — Team access (sign-in and roles, activity log, safe concurrent replays, secured Kafka, Slack team feed)
- [ ] v6.0 — Multi-cluster support and single sign-on

See [TODO.md](TODO.md) for the full backlog.

---

## Author

**Rohith Surakattula**
[GitHub](https://github.com/Surakattula-Rohith) · [LinkedIn](https://www.linkedin.com/in/surakattula-rohith-511315264/)
