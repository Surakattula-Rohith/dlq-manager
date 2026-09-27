package com.dlqmanager.util;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DlqHeadersTest {

    @Test
    void readsCustomErrorHeader() {
        Map<String, String> headers = Map.of("X-Error-Message", "DB Connection Timeout");

        assertThat(DlqHeaders.resolveErrorType(headers, "{}", null)).isEqualTo("DB Connection Timeout");
    }

    @Test
    void readsSpringKafkaErrorHeader() {
        Map<String, String> headers = Map.of("kafka_dlt-exception-message", "Payment gateway timeout");

        assertThat(DlqHeaders.resolveErrorType(headers, "{}", null)).isEqualTo("Payment gateway timeout");
    }

    @Test
    void readsKafkaConnectErrorHeader() {
        Map<String, String> headers = Map.of("__connect.errors.exception.message", "Converter failure");

        assertThat(DlqHeaders.resolveErrorType(headers, "{}", null)).isEqualTo("Converter failure");
    }

    @Test
    void customHeaderWinsWhenSeveralConventionsArePresent() {
        Map<String, String> headers = Map.of(
                "X-Error-Message", "From custom header",
                "kafka_dlt-exception-message", "From Spring Kafka"
        );

        assertThat(DlqHeaders.resolveErrorType(headers, "{}", null)).isEqualTo("From custom header");
    }

    @Test
    void blankHeaderIsIgnored() {
        Map<String, String> headers = Map.of(
                "X-Error-Message", "   ",
                "kafka_dlt-exception-message", "From Spring Kafka"
        );

        assertThat(DlqHeaders.resolveErrorType(headers, "{}", null)).isEqualTo("From Spring Kafka");
    }

    @Test
    void fallsBackToPayloadFieldWhenNoErrorHeader() {
        String payload = "{\"orderId\":\"ORD-1\",\"error\":{\"message\":\"Card declined\"}}";

        assertThat(DlqHeaders.resolveErrorType(Map.of(), payload, "error.message")).isEqualTo("Card declined");
    }

    @Test
    void fallsBackToExceptionClassThenUnknown() {
        Map<String, String> onlyClass = Map.of("kafka_dlt-exception-fqcn", "java.net.SocketTimeoutException");

        assertThat(DlqHeaders.resolveErrorType(onlyClass, "{}", null)).isEqualTo("java.net.SocketTimeoutException");
        assertThat(DlqHeaders.resolveErrorType(Map.of(), "{}", null)).isEqualTo(DlqHeaders.UNKNOWN_ERROR);
    }

    @Test
    void readJsonFieldHandlesBadInput() {
        assertThat(DlqHeaders.readJsonField("not json", "error")).isNull();
        assertThat(DlqHeaders.readJsonField("{\"a\":1}", "missing")).isNull();
        assertThat(DlqHeaders.readJsonField("{\"a\":1}", " ")).isNull();
        assertThat(DlqHeaders.readJsonField(null, "a")).isNull();
        assertThat(DlqHeaders.readJsonField("{\"a\":null}", "a")).isNull();
    }

    @Test
    void readJsonFieldReturnsNestedObjectsAsJson() {
        assertThat(DlqHeaders.readJsonField("{\"a\":{\"b\":2}}", "a")).isEqualTo("{\"b\":2}");
        assertThat(DlqHeaders.readJsonField("{\"a\":{\"b\":2}}", "a.b")).isEqualTo("2");
    }

    @Test
    void toMapKeepsNullHeaderValues() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Error-Message", "boom".getBytes(StandardCharsets.UTF_8));
        headers.add("empty", null);

        Map<String, String> map = DlqHeaders.toMap(headers);

        assertThat(map).containsEntry("X-Error-Message", "boom").containsEntry("empty", null);
    }

    @Test
    void identifiesHeadersToStripOnReplay() {
        assertThat(DlqHeaders.isDlqOnlyHeader("X-Error-Message")).isTrue();
        assertThat(DlqHeaders.isDlqOnlyHeader("kafka_dlt-exception-stacktrace")).isTrue();
        assertThat(DlqHeaders.isDlqOnlyHeader("__connect.errors.topic")).isTrue();

        assertThat(DlqHeaders.isDlqOnlyHeader("X-Original-Topic")).isFalse();
        assertThat(DlqHeaders.isDlqOnlyHeader("traceparent")).isFalse();
        assertThat(DlqHeaders.isDlqOnlyHeader(null)).isFalse();
    }
}
