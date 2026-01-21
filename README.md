# Kafka DLQ Manager

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![React](https://img.shields.io/badge/React-18-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6)
![Tailwind CSS](https://img.shields.io/badge/Tailwind-4.x-06B6D4)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-orange)
![License](https://img.shields.io/badge/License-AGPL--3.0-purple)

A production-ready Dead Letter Queue (DLQ) management system for Apache Kafka with a modern web dashboard. Browse, analyze, and replay failed messages through an intuitive UI or REST APIs.

> Stop losing failed messages. Track, analyze, and replay with confidence.

## Features

### Backend Features
- **DLQ Topic Management** - Register and manage DLQ topics with source topic mapping
- **Auto-Discovery** - Detect DLQ topics by naming convention (`-dlq`, `-error` suffixes)
- **Message Browsing** - Paginated message listing with payload, headers, and metadata
- **Error Analytics** - Error breakdown by type with percentages and patterns
- **Message Replay** - Single and bulk replay with audit trail and job tracking
- **Idempotent Producer** - Safe replay with duplicate prevention

### Frontend Features
- **Dashboard** - Overview with summary cards, DLQ status table, and recent replay activity
- **DLQ Topics Page** - List, search, add, edit, and delete DLQ topic registrations
- **Message Browser** - Browse messages with selection, filtering, and bulk actions
- **Message Detail Modal** - View full payload, headers, metadata with copy functionality
- **Error Breakdown Chart** - Visual breakdown of error types with percentages
- **Replay History** - Track all replay jobs with status, success rate, and timestamps
- **Settings** - Kafka cluster connection status and configuration
- **Responsive Design** - Clean, modern UI built with Tailwind CSS

## Tech Stack

### Frontend
| Technology | Purpose |
|------------|---------|
| React 18 | UI Framework |
| TypeScript | Type Safety |
| Vite | Build Tool & Dev Server |
| Tailwind CSS 4 | Styling |
| React Router 6 | Client-side Routing |
| TanStack Query | Server State Management |
| Axios | HTTP Client |
| Lucide React | Icons |
| Recharts | Charts (for analytics) |

### Backend
| Technology | Purpose |
|------------|---------|
| Java 17 | Runtime |
| Spring Boot 3.x | Framework |
| Spring Data JPA | Database ORM |
| Spring Kafka | Kafka Integration |
| PostgreSQL 15 | Metadata & Audit Storage |
| Apache Kafka 3.x | Message Queue |

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js 18+
- Maven

### 1. Start Infrastructure
```bash
docker-compose up -d
```
This starts Kafka (localhost:9092), Zookeeper (localhost:2181), and PostgreSQL (localhost:5432).

### 2. Run Backend
```bash
cd backend
./mvnw spring-boot:run
```
API available at: `http://localhost:8080`

### 3. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
Dashboard available at: `http://localhost:5173`

### 4. Create Test DLQ Topic
```bash
docker exec -it dlq-kafka kafka-topics --create \
  --topic orders-dlq \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

## Frontend Pages

### Dashboard (`/`)
- Summary cards showing total DLQ topics, replays, success rate, and alerts
- DLQ topics table with status indicators
- Recent replay jobs list
- Quick navigation to topic details

### DLQ Topics (`/dlq-topics`)
- Searchable list of all registered DLQ topics
- Add new DLQ topic with modal form
- Edit existing topic configuration
- Delete topics with confirmation
- Status badges (Active/Paused, Auto/Manual detection)

### Topic Detail (`/dlq-topics/:id`)
- Error breakdown visualization with progress bars
- Paginated message table with selection checkboxes
- Bulk replay selected messages
- Message detail modal with:
  - Metadata (offset, partition, key, timestamp)
  - Error information with exception class
  - Full headers view (JSON formatted)
  - Full payload view (JSON formatted)
  - Copy to clipboard functionality
  - Single message replay button

### Replay History (`/replay-history`)
- List of all replay jobs with filtering
- Status indicators (Completed, Failed, In Progress, Partial)
- Success rate progress bars
- Job details including message counts and timestamps

### Settings (`/settings`)
- Kafka cluster connection status
- Controller node information
- Cluster nodes list
- All Kafka topics with partition counts
- API configuration

## Frontend Structure

```
frontend/
├── src/
│   ├── api/                    # API layer
│   │   ├── client.ts           # Axios instance with interceptors
│   │   ├── dlqTopics.ts        # DLQ topics endpoints
│   │   ├── replay.ts           # Replay endpoints
│   │   └── kafka.ts            # Kafka admin endpoints
│   ├── components/
│   │   └── layout/
│   │       ├── Sidebar.tsx     # Navigation sidebar
│   │       ├── Header.tsx      # Page header with refresh
│   │       └── Layout.tsx      # Main layout wrapper
│   ├── pages/
│   │   ├── DashboardPage.tsx   # Home dashboard
│   │   ├── DlqTopicsPage.tsx   # Topics list & management
│   │   ├── DlqTopicDetailPage.tsx  # Message browser
│   │   ├── ReplayHistoryPage.tsx   # Replay jobs list
│   │   ├── AlertsPage.tsx      # Alerts (Phase 4)
│   │   └── SettingsPage.tsx    # Configuration
│   ├── types/
│   │   └── index.ts            # TypeScript interfaces
│   ├── App.tsx                 # Router setup
│   └── main.tsx                # Entry point
├── tailwind.config.js
├── vite.config.ts              # Vite config with API proxy
└── package.json
```

## Backend Structure

```
backend/
└── src/main/java/com/dlqmanager/
    ├── controller/
    │   ├── DlqTopicController.java   # DLQ CRUD endpoints
    │   ├── ReplayController.java     # Replay endpoints
    │   └── KafkaController.java      # Kafka admin endpoints
    ├── service/
    │   ├── DlqBrowserService.java    # Message browsing logic
    │   ├── ReplayService.java        # Replay orchestration
    │   └── KafkaAdminService.java    # Kafka admin operations
    ├── repository/
    │   ├── DlqTopicRepository.java
    │   └── ReplayJobRepository.java
    ├── model/
    │   ├── entity/                   # JPA entities
    │   ├── dto/                      # Request/Response DTOs
    │   └── enums/
    └── config/
        └── KafkaProducerConfig.java  # Idempotent producer
```

## API Reference

### DLQ Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dlq-topics` | List all registered DLQs |
| POST | `/api/dlq-topics` | Register new DLQ topic |
| GET | `/api/dlq-topics/{id}` | Get DLQ by ID |
| PUT | `/api/dlq-topics/{id}` | Update DLQ configuration |
| DELETE | `/api/dlq-topics/{id}` | Delete DLQ registration |

### Message Browsing & Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dlq-topics/{id}/messages?page=1&size=10` | Browse messages |
| GET | `/api/dlq-topics/{id}/message-count` | Get total message count |
| GET | `/api/dlq-topics/{id}/error-breakdown` | Get error statistics |

### Message Replay
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/replay/single` | Replay single message |
| POST | `/api/replay/bulk` | Replay multiple messages |
| GET | `/api/replay/jobs/{id}` | Get replay job status |
| GET | `/api/replay/history` | Get all replay jobs |

### Kafka Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/kafka/topics` | List all Kafka topics |
| GET | `/api/kafka/discover-dlqs` | Auto-discover DLQ topics |
| GET | `/api/kafka/cluster-info` | Get cluster information |

## Usage Examples

### Register a DLQ Topic
```bash
curl -X POST http://localhost:8080/api/dlq-topics \
  -H "Content-Type: application/json" \
  -d '{
    "dlqTopicName": "orders-dlq",
    "sourceTopic": "orders",
    "detectionType": "MANUAL",
    "errorFieldPath": "headers.X-Error-Message"
  }'
```

### Get Error Breakdown
```bash
curl "http://localhost:8080/api/dlq-topics/{id}/error-breakdown"
```

Response:
```json
{
  "success": true,
  "totalMessages": 51,
  "errorBreakdown": [
    {
      "errorType": "DB Connection Timeout",
      "count": 32,
      "percentage": 62.75
    },
    {
      "errorType": "Invalid JSON Format",
      "count": 12,
      "percentage": 23.53
    }
  ]
}
```

### Replay Messages (Bulk)
```bash
curl -X POST http://localhost:8080/api/replay/bulk \
  -H "Content-Type: application/json" \
  -d '{
    "dlqTopicId": "abc-123-uuid",
    "messages": [
      {"offset": 51, "partition": 0},
      {"offset": 52, "partition": 0}
    ],
    "initiatedBy": "admin@example.com"
  }'
```

Response:
```json
{
  "success": true,
  "message": "All 2 messages replayed successfully",
  "replayJob": {
    "id": "job-uuid",
    "status": "COMPLETED",
    "totalMessages": 2,
    "succeeded": 2,
    "failed": 0,
    "successRate": 100.0
  }
}
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        FRONTEND                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐       │
│  │ Dashboard │ │DLQ Topics │ │  Message  │ │  Replay   │       │
│  │   Page    │ │   Page    │ │  Browser  │ │  History  │       │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘       │
│         │            │             │             │               │
│  ┌──────┴────────────┴─────────────┴─────────────┴──────┐      │
│  │              TanStack Query + Axios                   │      │
│  └──────────────────────────┬────────────────────────────┘      │
└─────────────────────────────┼───────────────────────────────────┘
                              │ HTTP/REST
┌─────────────────────────────┼───────────────────────────────────┐
│                        BACKEND                                   │
│  ┌──────────────────────────┴────────────────────────────┐      │
│  │                    REST API Layer                      │      │
│  │  DlqTopicController · ReplayController · KafkaController     │
│  └──────────────────────────┬────────────────────────────┘      │
│                              │                                   │
│  ┌──────────────────────────┴────────────────────────────┐      │
│  │                    Service Layer                       │      │
│  │  DlqBrowserService · ReplayService · KafkaAdminService │      │
│  └────────────┬─────────────────────────────┬────────────┘      │
│               │                             │                    │
│               ▼                             ▼                    │
│        ┌────────────┐                ┌────────────┐             │
│        │ PostgreSQL │                │   Kafka    │             │
│        │  Metadata  │                │  Messages  │             │
│        │   Audit    │                │  Produce   │             │
│        │   Trail    │                │  Consume   │             │
│        └────────────┘                └────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## Development

### Frontend Development
```bash
cd frontend
npm install          # Install dependencies
npm run dev          # Start dev server (http://localhost:5173)
npm run build        # Production build
npm run preview      # Preview production build
```

### Backend Development
```bash
cd backend
./mvnw spring-boot:run              # Start server
./mvnw test                         # Run tests
./mvnw package -DskipTests          # Build JAR
```

### Environment Variables

**Frontend** (`.env` or `vite.config.ts`):
```
VITE_API_URL=http://localhost:8080   # Backend URL (optional, uses proxy in dev)
```

**Backend** (`application.yml`):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  datasource:
    url: jdbc:postgresql://localhost:5432/dlqmanager
```

## Message Header Format

DLQ messages should include these headers for full functionality:

```
X-Error-Message: Error description
X-Original-Topic: Source topic name
X-Consumer-Group: Consumer group name
X-Retry-Count: Number of retry attempts
X-Exception-Class: Java exception class
X-Failed-Timestamp: Epoch milliseconds
```

## Roadmap

- [x] Phase 1: DLQ Topic Management
- [x] Phase 2: Message Browsing & Error Analytics
- [x] Phase 3: Message Replay
- [x] Phase 4: Frontend Dashboard
- [ ] Phase 5: Alerting & Notifications

## Contributing

Contributions welcome. Fork the repo, create a branch, and submit a PR.

## License

AGPL-3.0 — See [LICENSE](LICENSE) for details.

## Author

**Rohith Surakattula**
[GitHub](https://github.com/Surakattula-Rohith) · [LinkedIn](https://www.linkedin.com/in/surakattula-rohith-511315264/)
