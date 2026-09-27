package com.dlqmanager.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.Locale;
import java.util.Map;

/**
 * Filters for browsing a DLQ
 *
 * @param search      free text, matched (case-insensitive) against the message key, payload and header values
 * @param errorType   exact error type, as shown in the error breakdown (e.g. "DB Connection Timeout")
 * @param pendingOnly true to hide messages that were already replayed
 */
public record MessageFilter(String search, String errorType, boolean pendingOnly) {

    public static MessageFilter none() {
        return new MessageFilter(null, null, false);
    }

    /**
     * Is any filter set? Without filters we can jump straight to a page;
     * with filters every message has to be checked.
     */
    public boolean isActive() {
        return hasText(search) || hasText(errorType) || pendingOnly;
    }

    /**
     * @param record            the raw Kafka record
     * @param headers           its headers as strings (see DlqHeaders.toMap)
     * @param resolvedErrorType its error type (see DlqHeaders.resolveErrorType)
     * @param replayed          whether it was already replayed successfully
     */
    public boolean matches(ConsumerRecord<String, String> record, Map<String, String> headers,
                           String resolvedErrorType, boolean replayed) {
        if (pendingOnly && replayed) {
            return false;
        }
        if (hasText(errorType) && !errorType.equals(resolvedErrorType)) {
            return false;
        }
        if (hasText(search)) {
            String needle = search.trim().toLowerCase(Locale.ROOT);
            return contains(record.key(), needle)
                    || contains(record.value(), needle)
                    || headers.values().stream().anyMatch(value -> contains(value, needle));
        }
        return true;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
