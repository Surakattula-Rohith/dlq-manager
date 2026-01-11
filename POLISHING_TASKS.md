# DLQ Manager - Polishing Tasks (Post Phase 3)

This document tracks quality improvements to be addressed after Phase 3 (Message Replay) is complete.

---

## 1. Error Handling

### Current Issues
- Generic `RuntimeException` thrown in services
- No custom exception hierarchy
- Frontend receives generic 500 errors

### Tasks
- [ ] Create custom exception classes:
  - `DlqTopicNotFoundException` (404)
  - `KafkaConnectionException` (503)
  - `InvalidPaginationException` (400)
  - `MessageNotFoundException` (404)
  - `ReplayFailedException` (500)
- [ ] Add `@ControllerAdvice` for global exception handling
- [ ] Return structured error responses with error codes
- [ ] Add proper HTTP status codes for each exception type

**Files to Update:**
- `backend/src/main/java/com/dlqmanager/exception/` (new package)
- All service classes
- `GlobalExceptionHandler.java` (new)

---

## 2. Input Validation

### Current Issues
- Basic validation in controllers only
- No validation on DTOs
- Missing business rule validation

### Tasks
- [ ] Add `@Valid` annotations on controller parameters
- [ ] Add validation annotations on DTOs:
  - `@NotNull`, `@NotEmpty`, `@Size`, `@Min`, `@Max`
- [ ] Validate topic names (regex pattern)
- [ ] Validate page number <= total pages
- [ ] Check if DLQ topic exists in Kafka before registration
- [ ] Validate source topic exists before creating DLQ mapping

**Files to Update:**
- All DTO classes (`DlqTopicDto`, `DlqMessageDto`, etc.)
- All controllers
- Add `pom.xml` dependency: `spring-boot-starter-validation`

---

## 3. Edge Case Handling

### Scenarios to Handle
- [ ] **Empty DLQ Topic**: No messages in topic
  - Current: Polls unnecessarily
  - Fix: Check message count first, return empty list
- [ ] **Kafka Unreachable**: Broker down or network issues
  - Current: Generic exception after timeout
  - Fix: Retry with exponential backoff, clear error message
- [ ] **Page Beyond Range**: User requests page 1000 of 6
  - Current: Returns empty list (confusing)
  - Fix: Return error with helpful message
- [ ] **Null Message Key**: Some messages don't have keys
  - Current: May cause NPE
  - Fix: Handle null keys gracefully, show "No Key"
- [ ] **Missing Error Headers**: Messages without DLQ headers
  - Current: Shows "Unknown Error"
  - Fix: This is actually correct, but log warning
- [ ] **Multi-partition Topics**: Currently only reads partition 0
  - Current: Missing messages from other partitions
  - Fix: Read from all partitions and merge (BIG TASK)
- [ ] **Offset Out of Range**: Seeking to non-existent offset
  - Current: May throw exception
  - Fix: Seek to earliest if out of range

**Files to Update:**
- `DlqBrowserService.java`
- `KafkaAdminService.java`
- All consumer/producer code

---

## 4. Performance Optimization

### Current Issues
- New consumer created per request
- Error breakdown reads ALL messages every time
- No caching
- No connection pooling

### Tasks
- [ ] **Consumer Pooling**: Reuse Kafka consumers
  - Investigate Spring Kafka's `@KafkaListener` vs manual consumers
  - Consider consumer pool with limited instances
- [ ] **Cache Error Breakdown**: Cache results for 5 minutes
  - Use Spring Cache with Redis
  - Invalidate on new messages (or time-based)
- [ ] **Pagination Optimization**: Don't seek from beginning each time
  - Consider offset-based cursor pagination
- [ ] **Database Indexes**: Add indexes for common queries
  - Index on `dlq_topics.dlq_topic_name`
  - Index on `replay_jobs.status`, `replay_jobs.created_at`
- [ ] **Batch Operations**: Read messages in larger batches
- [ ] **Lazy Loading**: Don't load message payload until needed

**Files to Update:**
- `DlqBrowserService.java`
- Add caching configuration
- Database migration scripts
- `application.properties` (cache config)

---

## 5. Code Quality

### Tasks
- [ ] **Extract Constants**:
  ```java
  // Before
  int maxPolls = 10;
  Duration timeout = Duration.ofSeconds(2);

  // After
  private static final int MAX_POLL_ATTEMPTS = 10;
  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);
  ```
- [ ] **Detailed Logging**:
  - Add DEBUG level logs for troubleshooting
  - Log request/response for all API calls
  - Log Kafka operations (seek, poll, produce)
- [ ] **Better Comments**:
  - Explain WHY, not WHAT
  - Document assumptions
  - Add examples in javadoc
- [ ] **Code Duplication**:
  - Extract consumer creation to utility
  - Share common validation logic
  - Reusable response wrapper

**Files to Update:**
- All service classes
- Add `Constants.java` or configuration properties
- Add `logback-spring.xml` for logging config

---

## 6. Testing

### Current State
- ❌ No unit tests
- ❌ No integration tests
- ❌ No API tests

### Tasks
- [ ] **Unit Tests** (70%+ coverage goal):
  - Test all service methods
  - Mock Kafka consumers/producers
  - Test edge cases (null, empty, invalid input)
  - Use JUnit 5 + Mockito
- [ ] **Integration Tests**:
  - Test actual Kafka operations
  - Use Testcontainers for Kafka + Postgres
  - Test full request/response cycle
- [ ] **API Tests**:
  - Use RestAssured or MockMvc
  - Test all endpoints
  - Test error scenarios
  - Validate response structure
- [ ] **Performance Tests**:
  - Test with large topics (100k+ messages)
  - Measure API response times
  - Check for memory leaks

**Files to Create:**
- `backend/src/test/java/com/dlqmanager/service/DlqBrowserServiceTest.java`
- `backend/src/test/java/com/dlqmanager/controller/DlqTopicControllerTest.java`
- Integration test package
- `pom.xml` test dependencies

---

## 7. Configuration & Deployment

### Tasks
- [ ] **Externalize Configuration**:
  - Move hardcoded values to `application.properties`
  - Support environment-specific configs (dev, prod)
  - Add `application-dev.yml`, `application-prod.yml`
- [ ] **Health Checks**:
  - Add `/actuator/health` endpoint
  - Check Kafka connectivity
  - Check database connectivity
- [ ] **Monitoring**:
  - Add Prometheus metrics
  - Expose custom metrics (messages browsed, replays executed)
  - Add Grafana dashboard config
- [ ] **Docker Optimization**:
  - Multi-stage build for smaller images
  - Health check in Docker Compose
  - Resource limits (memory, CPU)

**Files to Update:**
- `application.properties`
- `docker-compose.yml`
- Add Prometheus config
- Add health indicators

---

## 8. Multi-Partition Support (BIG TASK)

### Current Limitation
- **Only reads partition 0** of DLQ topics
- Missing messages from partitions 1, 2, 3, etc.

### Solution Design
```java
// For each partition:
1. Get all partition IDs for topic
2. Assign consumer to ALL partitions
3. Calculate offset for each partition (page-based)
4. Poll from all partitions
5. Merge and sort by timestamp
6. Return paginated results
```

### Challenges
- How to paginate across partitions?
- How to maintain order?
- How to calculate total message count?

### Tasks
- [ ] Get all partitions for a topic
- [ ] Modify `getMessages()` to read all partitions
- [ ] Merge messages from multiple partitions
- [ ] Update `getMessageCount()` to sum all partitions
- [ ] Update `getErrorBreakdown()` to read all partitions
- [ ] Test with multi-partition topics

**Files to Update:**
- `DlqBrowserService.java` (major refactor)
- Add `MultiPartitionConsumer.java` utility

---

## 9. User Experience Improvements

### Tasks
- [ ] **Better Error Messages**:
  - "Page 100 exceeds available 6 pages" instead of empty response
  - "Topic 'orders-dlq' not found in Kafka cluster"
  - "Failed to connect to Kafka broker at localhost:9092"
- [ ] **API Response Time Logging**:
  - Log slow requests (>2 seconds)
  - Add `X-Response-Time` header
- [ ] **Request Validation Summary**:
  - Return all validation errors at once (not one at a time)
- [ ] **API Documentation**:
  - Add Swagger/OpenAPI
  - Generate interactive API docs
  - Add request/response examples

**Files to Update:**
- All controllers
- Add `SwaggerConfig.java`
- Add OpenAPI annotations

---

## 10. Security (Optional for MVP)

### Tasks
- [ ] Add authentication (JWT)
- [ ] Add authorization (RBAC)
- [ ] Rate limiting per user
- [ ] Audit logging (who did what)
- [ ] Secure Kafka credentials
- [ ] HTTPS only

---

## 11. Database Optimizations

### Current Schema
- Basic tables created
- No indexes beyond primary keys
- No constraints

### Tasks
- [ ] Add foreign key constraints with proper cascade rules
- [ ] Add unique constraints where needed
- [ ] Add indexes for common queries:
  ```sql
  CREATE INDEX idx_dlq_topics_name ON dlq_topics(dlq_topic_name);
  CREATE INDEX idx_replay_jobs_status_date ON replay_jobs(status, created_at);
  CREATE INDEX idx_replay_messages_job_status ON replay_messages(replay_job_id, status);
  ```
- [ ] Add database migration versioning (Flyway or Liquibase)
- [ ] Add created_by, updated_by audit fields

---

## 12. Logging & Debugging

### Tasks
- [ ] Structured logging (JSON format)
- [ ] Correlation IDs for request tracing
- [ ] Log levels per package (DEBUG for dev, INFO for prod)
- [ ] Log aggregation setup (ELK stack or similar)
- [ ] Add request/response logging filter
- [ ] Log Kafka operations with timing

**Files to Create:**
- `logback-spring.xml`
- `RequestLoggingFilter.java`

---

## 13. Replay Count Indicator (Enhancement)

### Current Issue
- Users can replay the same message multiple times (intentional design)
- No way to know if a message was already replayed when browsing
- Can't see replay history for specific messages

### Proposed Feature
Add replay count and last replay timestamp when browsing DLQ messages.

**Example API Response:**
```json
{
  "messages": [
    {
      "messageKey": "ORD-78900",
      "offset": 51,
      "partition": 0,
      "payload": {...},
      "errorMessage": "DB Connection Timeout",
      "replayCount": 2,
      "lastReplayedAt": "2026-01-11T10:05:00Z",
      "lastReplayedBy": "test-user@company.com"
    }
  ]
}
```

### Benefits
- ✅ Users know which messages were already replayed
- ✅ Prevent accidental duplicate replays
- ✅ Better audit trail
- ✅ Can filter/sort by replay status

### Implementation Tasks
- [ ] Add query to join `replay_messages` with DLQ browsing
  ```sql
  SELECT
    rm.dlq_offset,
    COUNT(*) as replay_count,
    MAX(rm.replayed_at) as last_replayed_at,
    MAX(rj.initiated_by) as last_replayed_by
  FROM replay_messages rm
  JOIN replay_jobs rj ON rm.replay_job_id = rj.id
  WHERE rm.status = 'SUCCESS'
  GROUP BY rm.dlq_offset
  ```
- [ ] Add fields to `DlqMessageDto`:
  - `replayCount` (Integer, default 0)
  - `lastReplayedAt` (LocalDateTime, nullable)
  - `lastReplayedBy` (String, nullable)
- [ ] Update `DlqBrowserService.getMessages()`:
  - Query replay history for displayed offsets
  - Map replay data to messages
- [ ] Add repository method in `ReplayMessageRepository`:
  - `getReplayStatsForOffsets(List<Long> offsets)`
- [ ] Add optional filter parameter: `?includeReplayed=false`
  - Filter out already-replayed messages

### Alternative: Simple Indicator
Simpler version - just show if replayed or not (boolean):
```json
{
  "messageKey": "ORD-78900",
  "offset": 51,
  "wasReplayed": true  // Simple boolean
}
```

### Performance Consideration
- For large DLQs, this query might be slow
- Solution: Cache replay stats per topic
- Or: Only fetch on demand (separate endpoint)

**Priority:** P2 (Nice to have, not critical)

**Estimated Effort:** 3-4 hours

**Files to Update:**
- `DlqMessageDto.java`
- `DlqBrowserService.java`
- `ReplayMessageRepository.java`
- Postman collection (updated response examples)

---

## 14. Replay Options & Deletion (Enhancement)

### Current Behavior
Messages always stay in DLQ after replay (safe but DLQ grows forever)

### Proposed Feature
Add optional `deleteAfterReplay` parameter in replay request.

**Request Body:**
```json
{
  "dlqTopicId": "...",
  "messageOffset": 51,
  "messagePartition": 0,
  "deleteAfterReplay": true  // Optional, default: false
}
```

### Implementation Tasks
- [ ] Add `deleteAfterReplay` field to `ReplayRequestDto`
- [ ] Implement message deletion in `ReplayService`:
  ```java
  if (request.isDeleteAfterReplay() && replaySuccessful) {
      // Kafka doesn't support individual message deletion
      // Options:
      // 1. Mark as "processed" in database (logical deletion)
      // 2. Use Kafka Streams to filter and republish
      // 3. Document that physical deletion isn't possible
  }
  ```
- [ ] **Note:** Kafka doesn't support deleting individual messages!
  - Only option: delete entire topic or use log compaction
  - For MVP: Add "processed" flag in database instead

### Alternative: Logical Deletion
Instead of physical deletion from Kafka:
- Add `processed` boolean to `replay_messages`
- Filter out processed messages when browsing
- User can "archive" messages without actually deleting

**Priority:** P2 (Nice to have)

**Estimated Effort:** 4-6 hours

---

## Priority Order (When Polishing)

### Must Have (P0)
1. Custom exceptions and error handling
2. Input validation on DTOs
3. Edge case handling (empty topics, null keys)
4. Multi-partition support (critical for real use)
5. Basic unit tests

### Should Have (P1)
6. Performance optimization (caching, pooling)
7. Configuration externalization
8. Better logging and debugging
9. Integration tests
10. Health checks

### Nice to Have (P2)
11. API documentation (Swagger)
12. Monitoring (Prometheus)
13. Security (auth/authz)
14. Advanced performance testing

---

## Estimated Effort

- P0 tasks: 3-4 days
- P1 tasks: 3-4 days
- P2 tasks: 2-3 days
- **Total: ~8-11 days**

---

## Notes

- This file will be updated as we discover more issues during Phase 3
- Some polishing tasks may be blocked by Phase 3 implementation
- Prioritize based on actual pain points during testing
- Don't over-engineer - ship first, perfect later

---

Last Updated: 2026-01-11
