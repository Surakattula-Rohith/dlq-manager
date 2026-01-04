package com.dlqmanager.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.time.Instant;
import java.util.HashMap;
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
     * Error message extracted from headers
     * Example: "DB Connection Timeout"
     */
    private String errorMessage;

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
     * This is where the "magic" happens:
     * 1. Extract basic Kafka metadata (partition, offset, timestamp)
     * 2. Parse headers (convert bytes to strings)
     * 3. Extract DLQ-specific headers (error message, retry count, etc.)
     * 4. Parse JSON payload
     *
     * @param record Raw Kafka ConsumerRecord
     * @return Formatted DlqMessageDto ready for API response
     */
    public static DlqMessageDto fromConsumerRecord(ConsumerRecord<String, String> record) {
        DlqMessageDto dto = new DlqMessageDto();

        // Basic Kafka metadata
        dto.setMessageKey(record.key());
        dto.setPartition(record.partition());
        dto.setOffset(record.offset());
        dto.setTimestamp(Instant.ofEpochMilli(record.timestamp()).toString());

        // Parse headers (convert from byte[] to String)
        Map<String, String> headersMap = new HashMap<>();
        for (Header header : record.headers()) {
            String key = header.key();
            String value = new String(header.value());  // byte[] → String
            headersMap.put(key, value);
        }
        dto.setHeaders(headersMap);

        // Extract DLQ-specific headers
        dto.setErrorMessage(headersMap.get("X-Error-Message"));
        dto.setOriginalTopic(headersMap.get("X-Original-Topic"));
        dto.setConsumerGroup(headersMap.get("X-Consumer-Group"));

        // Parse retry count (String → Integer)
        String retryCountStr = headersMap.get("X-Retry-Count");
        if (retryCountStr != null) {
            try {
                dto.setRetryCount(Integer.parseInt(retryCountStr));
            } catch (NumberFormatException e) {
                dto.setRetryCount(null);
            }
        }

        // Parse failed timestamp (epoch millis String → ISO-8601)
        String failedTimestampStr = headersMap.get("X-Failed-Timestamp");
        if (failedTimestampStr != null) {
            try {
                long epochMillis = Long.parseLong(failedTimestampStr);
                dto.setFailedTimestamp(Instant.ofEpochMilli(epochMillis).toString());
            } catch (NumberFormatException e) {
                dto.setFailedTimestamp(null);
            }
        }

        // Parse JSON payload (String → JsonNode)
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonPayload = objectMapper.readTree(record.value());
            dto.setPayload(jsonPayload);
        } catch (Exception e) {
            // If payload is not valid JSON, store as text node
            ObjectMapper objectMapper = new ObjectMapper();
            dto.setPayload(objectMapper.valueToTree(record.value()));
        }

        return dto;
    }
}
