# Kafka DLQ Manager

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-orange)
![License](https://img.shields.io/badge/License-AGPL--3.0-purple)

A production-ready Dead Letter Queue (DLQ) management system for Apache Kafka. Browse, analyze, and replay failed messages through REST APIs.

> Stop losing failed messages. Track, analyze, and replay with confidence.

## Features

**DLQ Management**
- Register and manage DLQ topics with source topic mapping
- Auto-discover DLQ topics by naming convention (`-dlq`, `-error` suffixes)
- Active/Paused status management

**Message Browsing**
- Browse messages with pagination (up to 100 per page)
- View payload, headers, and metadata
- Extract error information from Kafka headers

**Error Analytics**
- Error breakdown by type with percentages
- Identify most common failure patterns
- Prioritize fixes based on error frequency

**Message Replay**
- Replay single or bulk messages back to source topic
- Complete audit trail with job tracking
- Header cleanup (removes DLQ headers, adds replay markers)
- Idempotent producer configuration

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.x, Spring Data JPA |
| Database | PostgreSQL 15 |
| Messaging | Apache Kafka 3.x |
| Infrastructure | Docker Compose |

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
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

### 3. Create Test DLQ Topic
```bash
docker exec -it dlq-kafka kafka-topics --create \
  --topic orders-dlq \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

### 4. Test with Postman
Import `DLQ_Manager_Full_API.postman_collection.json` into Postman.

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
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                        │
│  DlqTopicController · ReplayController · KafkaController │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────┐
│                    Service Layer                         │
│  DlqBrowserService · ReplayService · KafkaAdminService  │
└──────────┬─────────────────────────────────┬────────────┘
           │                                 │
           ▼                                 ▼
    ┌────────────┐                    ┌────────────┐
    │ PostgreSQL │                    │   Kafka    │
    │  Metadata  │                    │  Messages  │
    │   Audit    │                    │  Produce   │
    │   Trail    │                    │  Consume   │
    └────────────┘                    └────────────┘
```

## Project Structure

```
dlq-manager/
├── backend/
│   └── src/main/java/com/dlqmanager/
│       ├── controller/     # REST endpoints
│       ├── service/        # Business logic
│       ├── repository/     # Data access
│       ├── model/
│       │   ├── entity/     # JPA entities
│       │   ├── dto/        # Request/Response objects
│       │   └── enums/
│       └── config/         # Kafka producer config
├── docker-compose.yml
└── DLQ_Manager_Full_API.postman_collection.json
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

## Contributing

Contributions welcome. Fork the repo, create a branch, and submit a PR.

## License

AGPL-3.0 — See [LICENSE](LICENSE) for details.

## Author

**Rohith Surakattula**  
[GitHub](https://github.com/Surakattula-Rohith) · [LinkedIn](https://www.linkedin.com/in/surakattula-rohith-511315264/)