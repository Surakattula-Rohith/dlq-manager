package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorBreakdownCacheTest {

    private static final Map<String, Long> BREAKDOWN = Map.of("DB Connection Timeout", 3L);

    private final KafkaConfigService kafkaConfigService = mock(KafkaConfigService.class);
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger scans = new AtomicInteger();
    private final DlqTopic topic = topic("orders-dlq");
    private ErrorBreakdownCache cache;

    @BeforeEach
    void setUp() {
        when(kafkaConfigService.getBootstrapServers()).thenReturn("kafka-a:9092");
        cache = new ErrorBreakdownCache(kafkaConfigService, Duration.ofSeconds(30), nanos::get);
    }

    @Test
    void remembersTheBreakdownUntilItExpires() {
        assertThat(cache.get(topic, countingScan())).isEqualTo(BREAKDOWN);
        advance(Duration.ofSeconds(29));
        assertThat(cache.get(topic, countingScan())).isEqualTo(BREAKDOWN);
        assertThat(scans).hasValue(1);

        advance(Duration.ofSeconds(2));
        cache.get(topic, countingScan());
        assertThat(scans).hasValue(2);
    }

    @Test
    void requestsArrivingDuringAScanWaitForItInsteadOfStartingTheirOwn() throws Exception {
        CountDownLatch scanStarted = new CountDownLatch(1);
        CountDownLatch finishScan = new CountDownLatch(1);
        List<Map<String, Long>> results = new ArrayList<>();

        Thread first = new Thread(() -> collect(results, cache.get(topic, () -> {
            scans.incrementAndGet();
            scanStarted.countDown();
            awaitQuietly(finishScan);
            return BREAKDOWN;
        })));
        first.start();
        assertThat(scanStarted.await(5, TimeUnit.SECONDS)).isTrue();

        List<Thread> waiting = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> collect(results, cache.get(topic, countingScan())));
            thread.start();
            waiting.add(thread);
        }
        // All four are parked on the running scan before it is allowed to finish
        await().atMost(Duration.ofSeconds(5)).until(() ->
                waiting.stream().allMatch(thread -> thread.getState() == Thread.State.WAITING));

        finishScan.countDown();
        first.join(5_000);
        for (Thread thread : waiting) {
            thread.join(5_000);
        }

        assertThat(scans).hasValue(1);
        assertThat(results).hasSize(5).allMatch(BREAKDOWN::equals);
    }

    @Test
    void aFailedScanIsNotRemembered() {
        assertThatThrownBy(() -> cache.get(topic, () -> {
            throw new IllegalStateException("Kafka is down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Kafka is down");

        assertThat(cache.get(topic, countingScan())).isEqualTo(BREAKDOWN);
        assertThat(scans).hasValue(1);
    }

    @Test
    void requestsWaitingOnAScanThatFailsGetTheSameError() throws Exception {
        CountDownLatch scanStarted = new CountDownLatch(1);
        CountDownLatch finishScan = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        Thread first = new Thread(() -> collectError(errors, () -> cache.get(topic, () -> {
            scanStarted.countDown();
            awaitQuietly(finishScan);
            throw new IllegalStateException("Kafka is down");
        })));
        first.start();
        assertThat(scanStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread waiting = new Thread(() -> collectError(errors, () -> cache.get(topic, countingScan())));
        waiting.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> waiting.getState() == Thread.State.WAITING);

        finishScan.countDown();
        first.join(5_000);
        waiting.join(5_000);

        assertThat(scans).hasValue(0);
        assertThat(errors).hasSize(2).allSatisfy(error ->
                assertThat(error).isInstanceOf(IllegalStateException.class).hasMessage("Kafka is down"));
    }

    @Test
    void everyTopicHasItsOwnBreakdown() {
        cache.get(topic, countingScan());
        cache.get(topic("payments-dlq"), countingScan());

        assertThat(scans).hasValue(2);
    }

    @Test
    void changingTheErrorFieldScansAgain() {
        cache.get(topic, countingScan());

        topic.setErrorFieldPath("error.reason");
        cache.get(topic, countingScan());

        assertThat(scans).hasValue(2);
    }

    @Test
    void changingTheKafkaAddressScansAgain() {
        cache.get(topic, countingScan());

        when(kafkaConfigService.getBootstrapServers()).thenReturn("kafka-b:9092");
        cache.get(topic, countingScan());

        assertThat(scans).hasValue(2);
    }

    @Test
    void theSharedResultCannotBeChangedByOneRequest() {
        Map<String, Long> breakdown = cache.get(topic, countingScan());

        assertThatThrownBy(() -> breakdown.put("Other", 1L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void zeroTtlTurnsTheCacheOff() {
        cache = new ErrorBreakdownCache(kafkaConfigService, Duration.ZERO, nanos::get);

        cache.get(topic, countingScan());
        cache.get(topic, countingScan());

        assertThat(scans).hasValue(2);
    }

    private Supplier<Map<String, Long>> countingScan() {
        return () -> {
            scans.incrementAndGet();
            return BREAKDOWN;
        };
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    private static DlqTopic topic(String name) {
        DlqTopic topic = new DlqTopic();
        topic.setId(UUID.randomUUID());
        topic.setDlqTopicName(name);
        return topic;
    }

    private static void collect(List<Map<String, Long>> results, Map<String, Long> result) {
        synchronized (results) {
            results.add(result);
        }
    }

    private static void collectError(List<Throwable> errors, Runnable call) {
        try {
            call.run();
        } catch (Throwable e) {
            synchronized (errors) {
                errors.add(e);
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
