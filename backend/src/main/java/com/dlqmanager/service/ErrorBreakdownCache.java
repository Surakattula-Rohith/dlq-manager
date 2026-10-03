package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqTopic;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Error Breakdown Cache
 *
 * The error breakdown reads every message in a DLQ (up to the scan limit), so it is the
 * most expensive thing the message browser does. On a shared server several people often
 * have the same DLQ open, and each of them used to trigger a full scan.
 *
 * The result is now remembered for a short time (30 seconds by default):
 *
 * - Requests inside that window get the remembered result without touching Kafka
 * - Requests that arrive while a scan is still running wait for that scan instead of
 *   starting their own, so ten people opening a page at once cause one scan, not ten
 * - A failed scan is not remembered; the next request tries again
 *
 * The key holds everything the result depends on (topic name, error field, Kafka address),
 * so editing the topic or pointing the app at another cluster gives a fresh scan.
 *
 * Message counts are not cached: "pending" has to change right after a replay.
 */
@Component
public class ErrorBreakdownCache {

    private static final int MAX_TOPICS = 1_000;

    private final KafkaConfigService kafkaConfigService;
    private final Cache<Key, CompletableFuture<Map<String, Long>>> scans;

    @Autowired
    public ErrorBreakdownCache(KafkaConfigService kafkaConfigService,
                               @Value("${dlq.error-breakdown.cache-ttl:30s}") Duration ttl) {
        this(kafkaConfigService, ttl, Ticker.systemTicker());
    }

    ErrorBreakdownCache(KafkaConfigService kafkaConfigService, Duration ttl, Ticker ticker) {
        this.kafkaConfigService = kafkaConfigService;
        this.scans = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(MAX_TOPICS)
                .ticker(ticker)
                .build();
    }

    /**
     * Get the error breakdown for a topic, running the scan only if nobody has done it recently.
     *
     * @param dlqTopic the DLQ topic
     * @param scan     reads the topic and counts messages per error type
     * @return error type -> message count (read-only, shared between requests)
     */
    public Map<String, Long> get(DlqTopic dlqTopic, Supplier<Map<String, Long>> scan) {
        Key key = new Key(dlqTopic.getId(), dlqTopic.getDlqTopicName(), dlqTopic.getErrorFieldPath(),
                kafkaConfigService.getBootstrapServers());

        // The scan runs on this thread, outside the cache's own locks. Other requests for the
        // same topic find the future below and wait on it.
        CompletableFuture<Map<String, Long>> mine = new CompletableFuture<>();
        CompletableFuture<Map<String, Long>> shared = scans.asMap().putIfAbsent(key, mine);
        if (shared != null) {
            return resultOf(shared);
        }

        try {
            Map<String, Long> breakdown = Map.copyOf(scan.get());
            mine.complete(breakdown);
            return breakdown;
        } catch (RuntimeException | Error e) {
            scans.asMap().remove(key, mine);
            mine.completeExceptionally(e);
            throw e;
        }
    }

    private static Map<String, Long> resultOf(CompletableFuture<Map<String, Long>> scan) {
        try {
            return scan.join();
        } catch (CompletionException e) {
            // Report the scan's own error, the same one the request that ran it gets
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }

    private record Key(UUID dlqTopicId, String topicName, String errorFieldPath, String bootstrapServers) {
    }
}
