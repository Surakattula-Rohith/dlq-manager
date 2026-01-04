# DLQ Manager

A production-ready Dead Letter Queue (DLQ) management system for Apache Kafka. Monitor, browse, and analyze failed messages with comprehensive REST APIs.

> Stop losing failed messages. Track, analyze, and replay with confidence.

## Features

### ✅ DLQ Management
- Register and manage DLQ topics
- Auto-discover DLQ topics by naming convention
- CRUD operations for DLQ configurations
- Active/Paused status management
- Link DLQs to source topics

### ✅ Message Browsing
- Browse messages with pagination support
- View complete message details (payload, headers, metadata)
- Extract error information from Kafka headers
- Support for large message sets (up to 100 messages per page)

### ✅ Error Analytics
- Error breakdown by type with percentages
- Identify most common failure patterns
- Prioritize fixes based on error frequency
- Real-time message count

### ✅ Kafka Integration
- List all Kafka topics
- Check topic existence
- Get cluster information
- Kafka Admin API integration

### 🚧 Coming Soon
- Message replay (single and bulk)
- Advanced filtering and search
- Real-time dashboard
- Alert notifications

## Tech Stack

**Backend:**
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- Apache Kafka Clients
- PostgreSQL

**Infrastructure:**
- Apache Kafka 3.x
- Zookeeper
- PostgreSQL 15
- Docker Compose

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Maven

### 1. Start Infrastructure
```bash
docker-compose up -d
```

This starts:
- Kafka (localhost:9092)
- Zookeeper (localhost:2181)
- PostgreSQL (localhost:5432)

### 2. Run Backend
```bash
cd backend
./mvnw spring-boot:run
```

API will be available at: `http://localhost:8080`

### 3. Create Test DLQ Topic
```bash
docker exec -it dlq-kafka kafka-topics --create \
  --topic orders-dlq \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

### 4. Test with Postman
Import `DLQ_Manager_Full_API.postman_collection.json` into Postman and test all endpoints.

## API Endpoints

### Kafka Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/kafka/topics` | List all Kafka topics |
| GET | `/api/kafka/topics/{name}/exists` | Check if topic exists |
| GET | `/api/kafka/discover-dlqs` | Auto-discover DLQ topics |
| GET | `/api/kafka/cluster-info` | Get cluster information |

### DLQ Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dlq-topics` | List all registered DLQs |
| GET | `/api/dlq-topics/filter/active` | List active DLQs only |
| POST | `/api/dlq-topics` | Register new DLQ topic |
| GET | `/api/dlq-topics/{id}` | Get DLQ by ID |
| PUT | `/api/dlq-topics/{id}` | Update DLQ configuration |
| DELETE | `/api/dlq-topics/{id}` | Delete DLQ registration |

### Message Browsing & Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/dlq-topics/{id}/messages` | Browse messages with pagination |
| GET | `/api/dlq-topics/{id}/message-count` | Get total message count |
| GET | `/api/dlq-topics/{id}/error-breakdown` | Get error statistics |

**Query Parameters for `/messages`:**
- `page` (required): Page number, 1-based (min: 1)
- `size` (required): Messages per page (1-100)

## Example Usage

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

### Browse Messages
```bash
curl "http://localhost:8080/api/dlq-topics/{id}/messages?page=1&size=10"
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

## Message Format

Messages in DLQ topics should include these headers for proper tracking:

```
X-Error-Message: Error description
X-Original-Topic: Source topic name
X-Consumer-Group: Consumer group name
X-Retry-Count: Number of retry attempts
X-Exception-Class: Java exception class
X-Failed-Timestamp: Epoch milliseconds when failed
```

## Development

### Run Tests
```bash
cd backend
./mvnw test
```

### Build
```bash
cd backend
./mvnw clean package
```

### Database Access
```bash
# Connect to PostgreSQL
docker exec -it dlq-postgres psql -U dlquser -d dlqmanager

# View registered DLQs
SELECT * FROM dlq_topics;
```

### Kafka Commands
```bash
# List topics
docker exec -it dlq-kafka kafka-topics --list --bootstrap-server localhost:9092

# View messages in DLQ
docker exec -it dlq-kafka kafka-console-consumer \
  --topic orders-dlq \
  --from-beginning \
  --bootstrap-server localhost:9092 \
  --property print.headers=true
```

## Configuration

### application.properties
```properties
# Kafka
spring.kafka.bootstrap-servers=localhost:9092

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/dlqmanager
spring.datasource.username=dlquser
spring.datasource.password=dlqpass

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

## Project Structure
```
dlq-manager/
├── backend/
│   └── src/main/java/com/dlqmanager/
│       ├── controller/     # REST API endpoints
│       ├── service/        # Business logic
│       ├── repository/     # Database access
│       ├── model/
│       │   ├── entity/     # JPA entities
│       │   ├── dto/        # Data transfer objects
│       │   └── enums/      # Enums
│       └── util/           # Utilities
├── docker-compose.yml      # Infrastructure setup
└── DLQ_Manager_Full_API.postman_collection.json
```

## Architecture

```
┌─────────────┐
│   Postman   │
│  (Testing)  │
└──────┬──────┘
       │ HTTP
       ▼
┌─────────────────────────┐
│   DLQ Manager Backend   │
│   (Spring Boot)         │
├─────────────────────────┤
│ • DlqTopicController    │
│ • KafkaController       │
│ • DlqDiscoveryService   │
│ • DlqBrowserService     │
│ • KafkaAdminService     │
└────┬────────────────┬───┘
     │                │
     ▼                ▼
┌──────────┐    ┌──────────┐
│PostgreSQL│    │  Kafka   │
│(Metadata)│    │ (Messages)│
└──────────┘    └──────────┘
```

## Contributing

This is a personal project. Contributions are welcome!

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

AGPL-3.0 - See LICENSE file for details

## Author

Rohith Surakattula

## Status

**Phase 1 & 2 Complete** ✅
- DLQ registration and management
- Message browsing with pagination
- Error analytics

**Phase 3 (Next)** 🚧
- Message replay functionality
- Advanced filtering and search
