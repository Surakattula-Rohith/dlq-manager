package com.dlqmanager.model.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Time ranges for the DLQ trend chart
 *
 * LAST_24_HOURS - 24 points, one per hour
 * LAST_7_DAYS   - 28 points, one per 6 hours (00:00, 06:00, 12:00, 18:00)
 *
 * History is kept for 7 days (see AlertEvaluatorService), so 7 days is the longest range.
 */
public enum TrendRange {

    LAST_24_HOURS("24h", 24, 60),
    LAST_7_DAYS("7d", 28, 360);

    private final String code;
    private final int buckets;
    private final int bucketMinutes;

    TrendRange(String code, int buckets, int bucketMinutes) {
        this.code = code;
        this.buckets = buckets;
        this.bucketMinutes = bucketMinutes;
    }

    /**
     * Value used in the API: "24h" or "7d"
     */
    public String code() {
        return code;
    }

    public int buckets() {
        return buckets;
    }

    public int bucketMinutes() {
        return bucketMinutes;
    }

    public static Optional<TrendRange> fromCode(String code) {
        return Arrays.stream(values()).filter(range -> range.code.equals(code)).findFirst();
    }
}
