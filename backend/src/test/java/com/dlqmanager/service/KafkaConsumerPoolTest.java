package com.dlqmanager.service;

import com.dlqmanager.config.KafkaConnection;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerPoolTest {

    private final KafkaConfigService kafkaConfigService = mock(KafkaConfigService.class);
    private final List<KafkaConsumer<String, String>> created = new ArrayList<>();
    private KafkaConsumerPool pool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(kafkaConfigService.getConnection()).thenReturn(new KafkaConnection("kafka-a:9092"));
        pool = new KafkaConsumerPool(kafkaConfigService, connection -> {
            KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
            created.add(consumer);
            return consumer;
        });
    }

    @Test
    void reusesAConsumerThatWasGivenBack() {
        KafkaConsumer<String, String> first;
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            first = lease.consumer();
        }
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            assertThat(lease.consumer()).isSameAs(first);
        }

        assertThat(created).hasSize(1);
        verify(first, times(2)).unsubscribe();
        verify(first, never()).close(any(Duration.class));
    }

    @Test
    void requestsRunningAtTheSameTimeGetTheirOwnConsumer() {
        try (KafkaConsumerPool.Lease a = pool.borrow(); KafkaConsumerPool.Lease b = pool.borrow()) {
            assertThat(a.consumer()).isNotSameAs(b.consumer());
        }
        assertThat(pool.idleCount()).isEqualTo(2);
    }

    @Test
    void keepsAtMostMaxIdleConsumers() {
        List<KafkaConsumerPool.Lease> leases = IntStream.range(0, KafkaConsumerPool.MAX_IDLE + 2)
                .mapToObj(i -> pool.borrow())
                .toList();
        leases.forEach(KafkaConsumerPool.Lease::close);

        assertThat(pool.idleCount()).isEqualTo(KafkaConsumerPool.MAX_IDLE);
        verify(created.get(KafkaConsumerPool.MAX_IDLE)).close(any(Duration.class));
        verify(created.get(KafkaConsumerPool.MAX_IDLE + 1)).close(any(Duration.class));
    }

    @Test
    void consumerThatCannotBeResetIsClosedInsteadOfReused() {
        KafkaConsumer<String, String> broken;
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            broken = lease.consumer();
            doThrow(new IllegalStateException("broken")).when(broken).unsubscribe();
        }

        verify(broken).close(any(Duration.class));
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            assertThat(lease.consumer()).isNotSameAs(broken);
        }
    }

    @Test
    void newKafkaConnectionGetsNewConsumersAndClosesTheOldOnes() {
        KafkaConsumer<String, String> old;
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            old = lease.consumer();
        }

        when(kafkaConfigService.getConnection()).thenReturn(new KafkaConnection("kafka-b:9092"));
        try (KafkaConsumerPool.Lease lease = pool.borrow()) {
            assertThat(lease.consumer()).isNotSameAs(old);
        }

        verify(old).close(any(Duration.class));
    }

    @Test
    void givingBackTwiceOnlyCountsOnce() {
        KafkaConsumerPool.Lease lease = pool.borrow();
        lease.close();
        lease.close();

        assertThat(pool.idleCount()).isEqualTo(1);
    }

    @Test
    void shutdownClosesIdleConsumers() {
        pool.borrow().close();

        pool.close();

        assertThat(pool.idleCount()).isZero();
        verify(created.get(0)).close(any(Duration.class));
    }
}
