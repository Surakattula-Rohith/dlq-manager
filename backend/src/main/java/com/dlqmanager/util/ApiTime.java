package com.dlqmanager.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Times in API responses
 *
 * The app keeps its times as LocalDateTime in UTC (see DlqManagerApplication). Sent as
 * they are ("2026-10-06T15:06:46"), a browser reads them as its own local time and shows
 * the wrong hour everywhere outside UTC. So every time leaves the API as a UTC instant
 * with a trailing Z ("2026-10-06T15:06:46Z"), which the browser converts correctly.
 */
public final class ApiTime {

    private ApiTime() {
    }

    /**
     * @return the time as an ISO-8601 UTC instant, or null
     */
    public static String utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }
}
