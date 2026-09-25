package com.dlqmanager.model.dto;

import com.dlqmanager.util.DlqHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Message DTO
 *
 * Purpose: Represents a single message from a DLQ topic in API responses
 *
 * Why we need this:
 * - Kafka's ConsumerRecord is too technical for API responses
 * - We need to extract and format error information from headers
 * - Payload should be parsed as JSON, not raw string
 * - Timestamps should be human-readable
 *
 * This DTO transforms raw Kafka data into a clean, user-friendly format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DlqMessageDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Message key (e.g., "ORD-78900")
     * Used for message identification and partitioning
     */
    private String messageKey;

    /**
     * Message payload as JSON object
     * Original: String → Parsed: JsonNode (can be object, array, etc.)
     */
    private JsonNode payload;

    /**
     * Kafka partition this message is in
     */
    private Integer partition;

    /**
     * Offset within the partition
     * Like a row number - uniquely identifies message within partition
     */
    private Long offset;

    /**
     * When the message was originally created (epoch millis → ISO-8601)
     */
    private String timestamp;

    /**
     * Error message extracted from headers (or from the payload, if the
     * DLQ topic has an errorFieldPath configured)
     * Example: "DB Connection Timeout"
     */
    private String errorMessage;

    /**
     * Exception class that caused the failure
     * Example: "java.sql.SQLException"
     */
    private String exceptionClass;

    /**
     * Original topic this message came from (before going to DLQ)
     * Example: "orders"
     */
    private String originalTopic;

    /**
     * How many times the consumer tried to process this message
     */
    private Integer retryCount;

    /**
     * When the message failed and was sent to DLQ
     */
    private String failedTimestamp;

    /**
     * Consumer group that failed to process this message
     */
    private String consumerGroup;

    /**
     * All headers as a map (for debugging)
     * Key: header name, Value: header value as string
     */
    private Map<String, String> headers;

    /**
     * Factory method: Convert Kafka ConsumerRecord to DlqMessageDto
     *
     * @param record Raw Kafka ConsumerRecord
     * @return Formatted DlqMessageDto ready for API response
     */
    public static DlqMessageDto fromConsumerRecord(ConsumerRecord<String, String> record) {
        return fromConsumerRecord(record, null);
    }

    /**
     * Factory method: Convert Kafka ConsumerRecord to DlqMessageDto
     *
     * This is where the "magic" happens:
     * 1. Extract basic Kafka metadata (partition, offset, timestamp)
     * 2. Parse headers (convert bytes to strings)
     * 3. Extract DLQ-specific headers (supports custom X-*, Spring Kafka and Kafka Connect headers)
     * 4. Parse JSON payload
     *
     * @param record Raw Kafka ConsumerRecord
     * @param errorFieldPath optional JSON path (e.g. "error.message") to read the error from the payload
     * @return Formatted DlqMessageDto ready for API response
     */
    public static DlqMessageDto fromConsumerRecord(ConsumerRecord<String, String> record, String errorFieldPath) {
        DlqMessageDto dto = new DlqMessageDto();

        // Basic Kafka metadata
        dto.setMessageKey(record.key());
        dto.setPartition(record.partition());
        dto.setOffset(record.offset());
        dto.setTimestamp(Instant.ofEpochMilli(record.timestamp()).toString());

        // Parse headers (convert from byte[] to String, null-safe)
        Map<String, String> headersMap = DlqHeaders.toMap(record.headers());
        dto.setHeaders(headersMap);

        // Extract DLQ-specific headers
        String errorMessage = DlqHeaders.firstValue(headersMap, DlqHeaders.ERROR_MESSAGE);
        if (errorMessage == null) {
            errorMessage = DlqHeaders.readJsonField(record.value(), errorFieldPath);
        }
        dto.setErrorMessage(errorMessage);
        dto.setExceptionClass(DlqHeaders.firstValue(headersMap, DlqHeaders.EXCEPTION_CLASS));
        dto.setOriginalTopic(DlqHeaders.firstValue(headersMap, DlqHeaders.ORIGINAL_TOPIC));
        dto.setConsumerGroup(DlqHeaders.firstValue(headersMap, DlqHeaders.CONSUMER_GROUP));

        // Parse retry count (String → Integer)
        String retryCountStr = DlqHeaders.firstValue(headersMap, DlqHeaders.RETRY_COUNT);
        if (retryCountStr != null) {
            try {
                dto.setRetryCount(Integer.parseInt(retryCountStr.trim()));
            } catch (NumberFormatException e) {
                dto.setRetryCount(null);
            }
        }

        // Parse failed timestamp (epoch millis String → ISO-8601)
        String failedTimestampStr = DlqHeaders.firstValue(headersMap, DlqHeaders.FAILED_TIMESTAMP);
        if (failedTimestampStr != null) {
            try {
                long epochMillis = Long.parseLong(failedTimestampStr.trim());
                dto.setFailedTimestamp(Instant.ofEpochMilli(epochMillis).toString());
            } catch (NumberFormatException e) {
                dto.setFailedTimestamp(null);
            }
        }

        // Parse JSON payload (String → JsonNode)
        try {
            JsonNode jsonPayload = OBJECT_MAPPER.readTree(record.value());
            dto.setPayload(jsonPayload);
        } catch (Exception e) {
            // If payload is not valid JSON, store as text node
            dto.setPayload(OBJECT_MAPPER.valueToTree(record.value()));
        }

        return dto;
    }
}
