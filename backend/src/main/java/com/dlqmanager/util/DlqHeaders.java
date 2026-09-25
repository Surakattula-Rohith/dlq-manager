package com.dlqmanager.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DLQ header conventions
 *
 * Different producers write failure details into different header names.
 * This class knows the common ones so the rest of the app doesn't have to:
 *
 * 1. Custom "X-*" headers (used by TestDataProducer and many hand-rolled DLQs)
 *    X-Error-Message, X-Exception-Class, X-Original-Topic, ...
 *
 * 2. Spring Kafka DeadLetterPublishingRecoverer
 *    kafka_dlt-exception-message, kafka_dlt-exception-fqcn, kafka_dlt-original-topic, ...
 *
 * 3. Kafka Connect (errors.deadletterqueue.context.headers.enable=true)
 *    __connect.errors.exception.message, __connect.errors.exception.class.name, __connect.errors.topic, ...
 *
 * Lookups go through the lists in order and return the first non-blank value.
 */
public final class DlqHeaders {

    public static final List<String> ERROR_MESSAGE = List.of(
            "X-Error-Message",
            "kafka_dlt-exception-message",
            "__connect.errors.exception.message"
    );

    public static final List<String> EXCEPTION_CLASS = List.of(
            "X-Exception-Class",
            "kafka_dlt-exception-fqcn",
            "__connect.errors.exception.class.name"
    );

    public static final List<String> ORIGINAL_TOPIC = List.of(
            "X-Original-Topic",
            "kafka_dlt-original-topic",
            "__connect.errors.topic"
    );

    public static final List<String> CONSUMER_GROUP = List.of(
            "X-Consumer-Group",
            "kafka_dlt-original-consumer-group"
    );

    // Spring Kafka's kafka_dlt-original-timestamp is not included on purpose:
    // it is a binary long and it is the original produce time, not the failure time.
    public static final List<String> FAILED_TIMESTAMP = List.of(
            "X-Failed-Timestamp"
    );

    public static final List<String> RETRY_COUNT = List.of(
            "X-Retry-Count"
    );

    /**
     * Headers that only make sense inside the DLQ.
     * They are stripped when a message is replayed back to its source topic.
     */
    public static final List<String> DLQ_ONLY_HEADERS = List.of(
            "X-Error-Message",
            "X-Retry-Count",
            "X-Exception-Class",
            "X-Failed-Timestamp",
            "X-Consumer-Group"
    );

    /**
     * Header prefixes written by Spring Kafka and Kafka Connect.
     * Everything under these prefixes is DLQ metadata (including full stack traces).
     */
    public static final List<String> DLQ_ONLY_PREFIXES = List.of(
            "kafka_dlt-",
            "__connect.errors."
    );

    public static final String UNKNOWN_ERROR = "Unknown Error";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DlqHeaders() {
    }

    /**
     * Convert Kafka headers to a String map.
     * Header values can be null in Kafka, so they are handled safely.
     * If a header key appears more than once, the last value wins (same as before).
     */
    public static Map<String, String> toMap(Headers headers) {
        Map<String, String> map = new LinkedHashMap<>();
        if (headers == null) {
            return map;
        }
        for (Header header : headers) {
            map.put(header.key(), asString(header.value()));
        }
        return map;
    }

    /**
     * Return the first non-blank value found for any of the given header names.
     */
    public static String firstValue(Map<String, String> headers, List<String> names) {
        for (String name : names) {
            String value = headers.get(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Should this header be dropped when replaying to the source topic?
     */
    public static boolean isDlqOnlyHeader(String key) {
        if (key == null) {
            return false;
        }
        if (DLQ_ONLY_HEADERS.contains(key)) {
            return true;
        }
        for (String prefix : DLQ_ONLY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Work out the error label for a DLQ message.
     *
     * Order:
     * 1. Error message header (any convention)
     * 2. Field inside the JSON payload, if the DLQ topic has an errorFieldPath configured
     *    (dot notation, e.g. "error.message")
     * 3. Exception class header
     * 4. "Unknown Error"
     */
    public static String resolveErrorType(Map<String, String> headers, String payload, String errorFieldPath) {
        String error = firstValue(headers, ERROR_MESSAGE);
        if (error != null) {
            return error;
        }

        String fromPayload = readJsonField(payload, errorFieldPath);
        if (fromPayload != null) {
            return fromPayload;
        }

        String exceptionClass = firstValue(headers, EXCEPTION_CLASS);
        if (exceptionClass != null) {
            return exceptionClass;
        }

        return UNKNOWN_ERROR;
    }

    /**
     * Read a value from a JSON payload using dot notation ("error.message").
     * Returns null if the payload is not JSON, the path is empty, or the field is missing.
     */
    public static String readJsonField(String payload, String path) {
        if (payload == null || path == null || path.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(payload);
            for (String part : path.trim().split("\\.")) {
                if (node == null) {
                    return null;
                }
                node = node.get(part);
            }
            if (node == null || node.isNull()) {
                return null;
            }
            String value = node.isValueNode() ? node.asText() : node.toString();
            return value.isBlank() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }
}
