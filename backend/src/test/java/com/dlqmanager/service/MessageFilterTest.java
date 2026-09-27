package com.dlqmanager.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageFilterTest {

    private static final ConsumerRecord<String, String> RECORD =
            new ConsumerRecord<>("orders-dlq", 0, 7L, "ORD-78901", "{\"orderId\":\"ORD-78901\",\"city\":\"Bangalore\"}");
    private static final Map<String, String> HEADERS = Map.of("X-Consumer-Group", "order-processor-group");

    @Test
    void noFilterMatchesEverything() {
        assertThat(MessageFilter.none().isActive()).isFalse();
        assertThat(MessageFilter.none().matches(RECORD, HEADERS, "DB Connection Timeout", true)).isTrue();
    }

    @Test
    void searchLooksAtKeyPayloadAndHeadersIgnoringCase() {
        assertThat(search("ord-78901").matches(RECORD, HEADERS, "x", false)).isTrue();
        assertThat(search("bangalore").matches(RECORD, HEADERS, "x", false)).isTrue();
        assertThat(search("processor").matches(RECORD, HEADERS, "x", false)).isTrue();
        assertThat(search("ORD-99999").matches(RECORD, HEADERS, "x", false)).isFalse();
    }

    @Test
    void blankSearchIsIgnored() {
        MessageFilter filter = new MessageFilter("   ", null, false);

        assertThat(filter.isActive()).isFalse();
        assertThat(filter.matches(RECORD, HEADERS, "x", false)).isTrue();
    }

    @Test
    void errorTypeMustMatchExactly() {
        MessageFilter filter = new MessageFilter(null, "DB Connection Timeout", false);

        assertThat(filter.matches(RECORD, HEADERS, "DB Connection Timeout", false)).isTrue();
        assertThat(filter.matches(RECORD, HEADERS, "Validation Failed", false)).isFalse();
    }

    @Test
    void pendingOnlyHidesReplayedMessages() {
        MessageFilter filter = new MessageFilter(null, null, true);

        assertThat(filter.matches(RECORD, HEADERS, "x", false)).isTrue();
        assertThat(filter.matches(RECORD, HEADERS, "x", true)).isFalse();
    }

    @Test
    void allFiltersMustMatchTogether() {
        MessageFilter filter = new MessageFilter("bangalore", "DB Connection Timeout", true);

        assertThat(filter.matches(RECORD, HEADERS, "DB Connection Timeout", false)).isTrue();
        assertThat(filter.matches(RECORD, HEADERS, "Validation Failed", false)).isFalse();
        assertThat(filter.matches(RECORD, HEADERS, "DB Connection Timeout", true)).isFalse();
    }

    private static MessageFilter search(String text) {
        return new MessageFilter(text, null, false);
    }
}
