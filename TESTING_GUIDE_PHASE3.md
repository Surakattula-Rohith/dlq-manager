# Phase 3 Testing Guide - Message Replay

This guide walks you through testing the message replay functionality step-by-step.

---

## Prerequisites

Before testing, ensure you have:
- ✅ Docker Desktop running
- ✅ Postman installed
- ✅ Test messages in orders-dlq (from Phase 2)

---

## Step 1: Start Infrastructure

Open a terminal in the project root directory.

```bash
# Start Kafka, Zookeeper, and PostgreSQL
docker-compose up -d

# Verify all containers are running
docker ps
```

**Expected output:** You should see 3 containers running:
- `dlq-kafka`
- `dlq-zookeeper`
- `dlq-postgres`

---

## Step 2: Start the Backend

Open a new terminal and navigate to the backend directory:

```bash
cd backend

# Start Spring Boot application
./mvnw.cmd spring-boot:run
```

**Wait for this message in the logs:**
```
Started DlqManagerApplication in X.XXX seconds
```

**Common startup errors:**
- "Port 8080 already in use" → Kill the process using port 8080
- "Cannot connect to database" → Check if PostgreSQL container is running
- "Cannot connect to Kafka" → Check if Kafka container is running

---

## Step 3: Verify Existing Data

Before replaying, let's check what's already in the system.

### 3.1: Check if DLQ topic is registered

**Request:** `GET http://localhost:8080/api/dlq-topics`

**Expected response:**
```json
{
  "success": true,
  "count": 1,
  "dlqTopics": [
    {
      "id": "some-uuid-here",
      "dlqTopicName": "orders-dlq",
      "sourceTopic": "orders",
      ...
    }
  ]
}
```

**📝 Important:** Copy the `id` field - you'll need it for replay requests!

**If you get an empty list:**
- You need to register the DLQ first
- Use the POST /api/dlq-topics endpoint from Phase 1

### 3.2: Check messages in DLQ

**Request:** `GET http://localhost:8080/api/dlq-topics/{id}/messages?page=1&size=10`

Replace `{id}` with the UUID from step 3.1

**Expected response:**
```json
{
  "success": true,
  "messages": [
    {
      "messageKey": "ORD-78900",
      "offset": 0,
      "partition": 0,
      "payload": {...},
      "errorMessage": "DB Connection Timeout",
      ...
    },
    {
      "messageKey": "ORD-78901",
      "offset": 1,
      "partition": 0,
      ...
    }
  ],
  "pagination": {
    "totalMessages": 50,
    ...
  }
}
```

**📝 Important:** Note the `offset` and `partition` of a message you want to replay!

**If you get no messages:**
- Run the TestDataProducer to create test messages
- Check if the orders-dlq topic exists in Kafka

---

## Step 4: Test Single Message Replay

Now let's replay a message from the DLQ back to the source topic!

### 4.1: Prepare the replay request

**Endpoint:** `POST http://localhost:8080/api/replay/single`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "dlqTopicId": "PUT-THE-UUID-FROM-STEP-3.1-HERE",
  "messageOffset": 0,
  "messagePartition": 0,
  "initiatedBy": "your-name@company.com"
}
```

**What this does:**
- Reads message at offset 0, partition 0 from orders-dlq
- Sends it to the source topic (orders)
- Creates a replay job record in the database
- Returns the job details

### 4.2: Send the request

Click "Send" in Postman.

**Expected response (SUCCESS):**
```json
{
  "success": true,
  "message": "Message replayed successfully",
  "replayJob": {
    "id": "job-uuid-here",
    "dlqTopicId": "dlq-uuid",
    "dlqTopicName": "orders-dlq",
    "sourceTopic": "orders",
    "initiatedBy": "your-name@company.com",
    "status": "COMPLETED",
    "totalMessages": 1,
    "succeeded": 1,
    "failed": 0,
    "successRate": 100.0,
    "durationSeconds": 0,
    "createdAt": "2026-01-11T...",
    "startedAt": "2026-01-11T...",
    "completedAt": "2026-01-11T..."
  }
}
```

**✅ Success indicators:**
- `"success": true`
- `"status": "COMPLETED"`
- `"succeeded": 1`
- `"failed": 0`
- `"successRate": 100.0`

**Expected response (FAILURE):**
```json
{
  "success": false,
  "error": "Failed to replay message: ...",
  "status": 400
}
```

**Common failure reasons:**
1. "DLQ topic not found" → Wrong dlqTopicId UUID
2. "Message not found at offset" → Invalid offset or partition
3. "Topic 'orders' does not exist" → Source topic doesn't exist in Kafka
4. "Kafka timeout" → Kafka broker is down or unreachable

### 4.3: Check the backend logs

Look at your Spring Boot terminal. You should see logs like:

```
INFO  Starting single message replay for DLQ topic ID: ..., offset: 0, partition: 0
INFO  Found DLQ topic: orders-dlq → source topic: orders
INFO  Created replay job with ID: ...
INFO  Reading message from DLQ topic: orders-dlq, offset: 0, partition: 0
INFO  Successfully read message. Key: ORD-78900, Value length: XXX bytes
INFO  Sending message to source topic: orders
INFO  Message sent successfully. Partition: 0, Offset: XXX
INFO  Replay job completed successfully: ...
```

**If you see ERROR logs:**
- Read the error message carefully
- Check the stack trace for the root cause
- Common issues: Kafka connection, topic doesn't exist, offset out of range

---

## Step 5: Verify the Replay

Now let's verify that the message was actually sent to the source topic.

### 5.1: Check if source topic exists

**Terminal command:**
```bash
docker exec -it dlq-kafka kafka-topics --list --bootstrap-server localhost:9092
```

**Expected:** You should see "orders" in the list.

**If "orders" doesn't exist, create it:**
```bash
docker exec -it dlq-kafka kafka-topics --create \
  --topic orders \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

### 5.2: Read messages from source topic

**Terminal command:**
```bash
docker exec -it dlq-kafka kafka-console-consumer \
  --topic orders \
  --from-beginning \
  --bootstrap-server localhost:9092 \
  --max-messages 10 \
  --property print.headers=true \
  --property print.key=true
```

**Expected output:**
```
ORD-78900	X-Replayed-At:2026-01-11T10:30:00Z,X-Original-Topic:orders	{"orderId":"ORD-78900",...}
```

**✅ Verification checklist:**
- Message key matches (ORD-78900)
- Payload is the same JSON
- Headers include `X-Replayed-At` (proves it was replayed)
- DLQ headers removed (no X-Error-Message, X-Retry-Count)

---

## Step 6: Test Get Replay Job

Query the replay job you just created.

**Request:** `GET http://localhost:8080/api/replay/jobs/{jobId}`

Replace `{jobId}` with the `id` from the replay response in step 4.2

**Expected response:**
```json
{
  "success": true,
  "replayJob": {
    "id": "job-uuid",
    "status": "COMPLETED",
    "succeeded": 1,
    "failed": 0,
    ...
  }
}
```

---

## Step 7: Test Replay History

### 7.1: Get all replay history

**Request:** `GET http://localhost:8080/api/replay/history`

**Expected response:**
```json
{
  "success": true,
  "count": 1,
  "replayJobs": [
    {
      "id": "job-uuid",
      "dlqTopicName": "orders-dlq",
      "status": "COMPLETED",
      ...
    }
  ]
}
```

**Note:** Jobs are ordered by creation time (newest first)

### 7.2: Get replay history for specific DLQ

**Request:** `GET http://localhost:8080/api/replay/history/dlq/{dlqTopicId}`

Replace `{dlqTopicId}` with your DLQ UUID from step 3.1

**Expected response:**
```json
{
  "success": true,
  "dlqTopicId": "dlq-uuid",
  "count": 1,
  "replayJobs": [
    {
      "id": "job-uuid",
      "status": "COMPLETED",
      ...
    }
  ]
}
```

---

## Step 8: Verify Database Records

Let's check that the replay was recorded in PostgreSQL.

### 8.1: Connect to database

**Terminal command:**
```bash
docker exec -it dlq-postgres psql -U dlquser -d dlqmanager
```

### 8.2: Query replay_jobs table

**SQL query:**
```sql
SELECT id, status, total_messages, succeeded, failed, initiated_by, created_at
FROM replay_jobs
ORDER BY created_at DESC
LIMIT 5;
```

**Expected output:**
```
                  id                  | status    | total_messages | succeeded | failed |     initiated_by      |       created_at
--------------------------------------+-----------+----------------+-----------+--------+-----------------------+------------------------
 abc-123-...                          | COMPLETED |              1 |         1 |      0 | your-name@company.com | 2026-01-11 10:30:00
```

### 8.3: Query replay_messages table

**SQL query:**
```sql
SELECT id, message_key, status, error_message, replayed_at
FROM replay_messages
ORDER BY replayed_at DESC
LIMIT 5;
```

**Expected output:**
```
                  id                  | message_key | status  | error_message |       replayed_at
--------------------------------------+-------------+---------+---------------+------------------------
 xyz-456-...                          | ORD-78900   | SUCCESS | null          | 2026-01-11 10:30:00
```

**Exit psql:**
```sql
\q
```

---

## Step 9: Test Error Scenarios

Now let's test failure cases to ensure error handling works.

### 9.1: Test with invalid DLQ ID

**Request:** `POST http://localhost:8080/api/replay/single`

**Body:**
```json
{
  "dlqTopicId": "00000000-0000-0000-0000-000000000000",
  "messageOffset": 0,
  "messagePartition": 0
}
```

**Expected response:**
```json
{
  "success": false,
  "error": "DLQ topic not found: 00000000-0000-0000-0000-000000000000",
  "status": 400
}
```

### 9.2: Test with invalid offset

**Request:** `POST http://localhost:8080/api/replay/single`

**Body:**
```json
{
  "dlqTopicId": "YOUR-VALID-DLQ-UUID",
  "messageOffset": 999999,
  "messagePartition": 0
}
```

**Expected:** Should fail with "Message not found at offset" or "Offset out of range"

### 9.3: Test with missing required fields

**Request:** `POST http://localhost:8080/api/replay/single`

**Body:**
```json
{
  "dlqTopicId": "YOUR-VALID-DLQ-UUID"
}
```

**Expected response:**
```json
{
  "success": false,
  "error": "Message offset is required",
  "status": 400
}
```

---

## Step 10: Test Multiple Replays

Replay a few more messages to build up history.

**Replay message at offset 1:**
```json
{
  "dlqTopicId": "YOUR-DLQ-UUID",
  "messageOffset": 1,
  "messagePartition": 0,
  "initiatedBy": "test-user"
}
```

**Replay message at offset 2:**
```json
{
  "dlqTopicId": "YOUR-DLQ-UUID",
  "messageOffset": 2,
  "messagePartition": 0,
  "initiatedBy": "test-user"
}
```

**Then check history:**
`GET http://localhost:8080/api/replay/history`

**Expected:** Should show 3 replay jobs (offset 0, 1, 2)

---

## Testing Checklist

Mark these off as you test:

### Basic Functionality
- [ ] Register/verify DLQ topic exists
- [ ] Browse messages in DLQ
- [ ] Replay single message successfully
- [ ] Message appears in source topic
- [ ] Get replay job by ID
- [ ] Get all replay history
- [ ] Get replay history for specific DLQ

### Verification
- [ ] Check backend logs (no errors)
- [ ] Verify message in source topic (Kafka console consumer)
- [ ] Verify replay_jobs record in database
- [ ] Verify replay_messages record in database
- [ ] Verify X-Replayed-At header added
- [ ] Verify DLQ headers removed

### Error Handling
- [ ] Invalid DLQ UUID returns 400
- [ ] Invalid offset returns error
- [ ] Missing required fields returns 400
- [ ] Non-existent job ID returns 404

### Edge Cases
- [ ] Replay same message twice (should work)
- [ ] Replay first message (offset 0)
- [ ] Replay last message
- [ ] Replay with null initiatedBy (defaults to "system")

---

## Troubleshooting

### "Port 8080 already in use"
```bash
# Windows: Find and kill process
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### "Cannot connect to Kafka"
```bash
# Check if Kafka container is running
docker ps | grep kafka

# Check Kafka logs
docker logs dlq-kafka
```

### "Cannot connect to database"
```bash
# Check if PostgreSQL container is running
docker ps | grep postgres

# Check PostgreSQL logs
docker logs dlq-postgres
```

### "Topic 'orders' does not exist"
```bash
# Create the orders topic
docker exec -it dlq-kafka kafka-topics --create \
  --topic orders \
  --bootstrap-server localhost:9092 \
  --partitions 1 \
  --replication-factor 1
```

### "Message not found at offset"
- Check the offset exists: `GET /api/dlq-topics/{id}/messages`
- Offsets start at 0 (not 1)
- Make sure you have test data in the DLQ

### Backend won't start
```bash
# Clean and rebuild
cd backend
./mvnw.cmd clean package

# Then try again
./mvnw.cmd spring-boot:run
```

---

## Next Steps

After successfully testing single message replay, we'll add:
1. ✅ Bulk replay (replay multiple messages at once)
2. ✅ Enhanced history with message-level details
3. ✅ Update Postman collection

---

## Quick Reference

**Key UUIDs to save:**
- DLQ Topic ID: `_________________`
- First Replay Job ID: `_________________`

**Useful commands:**
```bash
# Start infrastructure
docker-compose up -d

# Start backend
cd backend && ./mvnw.cmd spring-boot:run

# Check Kafka topics
docker exec -it dlq-kafka kafka-topics --list --bootstrap-server localhost:9092

# Read from topic
docker exec -it dlq-kafka kafka-console-consumer --topic orders --from-beginning --bootstrap-server localhost:9092 --max-messages 5

# Connect to database
docker exec -it dlq-postgres psql -U dlquser -d dlqmanager
```

---

Good luck with testing! 🚀

If you encounter any issues, check:
1. Backend logs in the terminal
2. Docker container logs: `docker logs dlq-kafka` or `docker logs dlq-postgres`
3. This troubleshooting section above
