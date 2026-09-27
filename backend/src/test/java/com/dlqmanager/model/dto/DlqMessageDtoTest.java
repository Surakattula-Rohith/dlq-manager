package com.dlqmanager.model.dto;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DlqMessageDtoTest {

    @Test
    void convertsSpringKafkaDlqRecord() {
        ConsumerRecord<String, String> record = record("{\"orderId\":\"ORD-1\",\"amount\":1200}");
        header(record, "kafka_dlt-exception-message", "Payment gateway timeout");
        header(record, "kafka_dlt-exception-fqcn", "java.net.SocketTimeoutException");
        header(record, "kafka_dlt-original-topic", "orders");

        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record);

        assertThat(dto.getPartition()).isEqualTo(2);
        assertThat(dto.getOffset()).isEqualTo(41L);
        assertThat(dto.getMessageKey()).isEqualTo("ORD-1");
        assertThat(dto.getErrorMessage()).isEqualTo("Payment gateway timeout");
        assertThat(dto.getExceptionClass()).isEqualTo("java.net.SocketTimeoutException");
        assertThat(dto.getOriginalTopic()).isEqualTo("orders");
        assertThat(dto.getPayload().get("amount").asInt()).isEqualTo(1200);
        assertThat(dto.isReplayed()).isFalse();
    }

    @Test
    void readsErrorFromPayloadWhenTopicHasErrorFieldPath() {
        ConsumerRecord<String, String> record = record("{\"orderId\":\"ORD-1\",\"error\":\"Card declined\"}");

        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record, "error");

        assertThat(dto.getErrorMessage()).isEqualTo("Card declined");
    }

    @Test
    void parsesRetryCountAndFailedTimestamp() {
        ConsumerRecord<String, String> record = record("{}");
        header(record, "X-Retry-Count", "3");
        header(record, "X-Failed-Timestamp", "1767225600000");

        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record);

        assertThat(dto.getRetryCount()).isEqualTo(3);
        assertThat(dto.getFailedTimestamp()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void ignoresMalformedNumbersInHeaders() {
        ConsumerRecord<String, String> record = record("{}");
        header(record, "X-Retry-Count", "three");
        header(record, "X-Failed-Timestamp", "yesterday");

        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record);

        assertThat(dto.getRetryCount()).isNull();
        assertThat(dto.getFailedTimestamp()).isNull();
    }

    @Test
    void keepsNonJsonPayloadAsText() {
        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record("plain text, not JSON"));

        assertThat(dto.getPayload().isTextual()).isTrue();
        assertThat(dto.getPayload().asText()).isEqualTo("plain text, not JSON");
    }

    @Test
    void handlesHeaderWithNullValue() {
        ConsumerRecord<String, String> record = record("{}");
        record.headers().add("X-Error-Message", null);

        DlqMessageDto dto = DlqMessageDto.fromConsumerRecord(record);

        assertThat(dto.getErrorMessage()).isNull();
        assertThat(dto.getHeaders()).containsKey("X-Error-Message");
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("orders-dlq", 2, 41L, "ORD-1", value);
    }

    private static void header(ConsumerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
