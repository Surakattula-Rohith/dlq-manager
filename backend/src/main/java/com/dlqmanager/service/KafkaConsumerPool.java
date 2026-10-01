package com.dlqmanager.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;

/**
 * Kafka Consumer Pool
 *
 * Opening a Kafka consumer is slow: it connects to the brokers and fetches cluster
 * metadata (plus a TLS/SASL handshake on a secured cluster). Opening one per request
 * meant three new connections for every DLQ page view.
 *
 * Consumers are now borrowed from a small pool and given back after use:
 *
 *   try (KafkaConsumerPool.Lease lease = consumerPool.borrow()) {
 *       KafkaConsumer<String, String> consumer = lease.consumer();
 *       ...
 *   }
 *
 * - A KafkaConsumer is not thread-safe, so a consumer is only used by one request at a time
 * - Consumers read with assign() + seek() and never commit offsets, so one consumer can serve
 *   any topic; its assignment is cleared when it comes back
 * - When the Kafka address changes in Settings, idle consumers for the old address are closed
 * - At most MAX_IDLE consumers are kept; extra ones are closed when they come back
 */
@Component
@Slf4j
public class KafkaConsumerPool {

    static final int MAX_IDLE = 4;
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(2);

    private final KafkaConfigService kafkaConfigService;
    private final Function<String, KafkaConsumer<String, String>> consumerFactory;
    private final Deque<PooledConsumer> idle = new ArrayDeque<>();
    private boolean closed;

    @Autowired
    public KafkaConsumerPool(KafkaConfigService kafkaConfigService) {
        this(kafkaConfigService, KafkaConsumerPool::createConsumer);
    }

    KafkaConsumerPool(KafkaConfigService kafkaConfigService,
                      Function<String, KafkaConsumer<String, String>> consumerFactory) {
        this.kafkaConfigService = kafkaConfigService;
        this.consumerFactory = consumerFactory;
    }

    /**
     * Borrow a consumer for the current Kafka address. Close the lease to give it back.
     */
    public Lease borrow() {
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        List<PooledConsumer> outdated = new ArrayList<>();
        PooledConsumer reusable = null;

        synchronized (this) {
            while (reusable == null && !idle.isEmpty()) {
                PooledConsumer candidate = idle.pollFirst();
                if (candidate.bootstrapServers().equals(bootstrapServers)) {
                    reusable = candidate;
                } else {
                    outdated.add(candidate);
                }
            }
        }

        outdated.forEach(KafkaConsumerPool::closeQuietly);
        if (reusable == null) {
            reusable = new PooledConsumer(consumerFactory.apply(bootstrapServers), bootstrapServers);
        }
        return new Lease(reusable);
    }

    private void giveBack(PooledConsumer pooled) {
        try {
            // Forget the partitions the last request read, so the next one starts clean
            pooled.consumer().unsubscribe();
        } catch (Exception e) {
            log.warn("Closing a Kafka consumer that could not be reset: {}", e.getMessage());
            closeQuietly(pooled);
            return;
        }

        boolean keep;
        synchronized (this) {
            keep = !closed && idle.size() < MAX_IDLE;
            if (keep) {
                idle.addFirst(pooled);
            }
        }
        if (!keep) {
            closeQuietly(pooled);
        }
    }

    synchronized int idleCount() {
        return idle.size();
    }

    @PreDestroy
    public void close() {
        List<PooledConsumer> toClose;
        synchronized (this) {
            closed = true;
            toClose = new ArrayList<>(idle);
            idle.clear();
        }
        toClose.forEach(KafkaConsumerPool::closeQuietly);
    }

    private static void closeQuietly(PooledConsumer pooled) {
        try {
            pooled.consumer().close(CLOSE_TIMEOUT);
        } catch (Exception e) {
            log.debug("Error closing Kafka consumer", e);
        }
    }

    /**
     * Read-only consumers: no offset commits, never create topics.
     *
     * fetch.max.wait.ms is lowered from Kafka's 500 ms default. After a read, the consumer
     * already has the next fetch waiting at the broker; when it then moves to another
     * partition (or another request), it has to wait for that fetch to come back first.
     * With the default, every partition switch cost half a second. We read stored history,
     * not a live stream, so there is nothing to gain from the broker waiting for new data.
     */
    private static KafkaConsumer<String, String> createConsumer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-manager-browser-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 50);
        return new KafkaConsumer<>(props);
    }

    private record PooledConsumer(KafkaConsumer<String, String> consumer, String bootstrapServers) {
    }

    /**
     * A borrowed consumer. close() gives it back to the pool (only the first call counts).
     */
    public final class Lease implements AutoCloseable {

        private final PooledConsumer pooled;
        private boolean returned;

        private Lease(PooledConsumer pooled) {
            this.pooled = pooled;
        }

        public KafkaConsumer<String, String> consumer() {
            return pooled.consumer();
        }

        @Override
        public void close() {
            if (!returned) {
                returned = true;
                giveBack(pooled);
            }
        }
    }
}
