package com.dlqmanager.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTimeTest {

    @Test
    void timesLeaveTheApiAsUtcInstants() {
        String sent = ApiTime.utc(LocalDateTime.of(2026, 10, 6, 15, 6, 46));

        assertThat(sent).isEqualTo("2026-10-06T15:06:46Z");
        // What a browser in India (UTC+5:30) turns it into: 20:36 local, not 15:06
        assertThat(Instant.parse(sent)).isEqualTo(Instant.parse("2026-10-06T20:36:46+05:30"));
    }

    @Test
    void noTimeStaysEmpty() {
        assertThat(ApiTime.utc(null)).isNull();
    }
}
