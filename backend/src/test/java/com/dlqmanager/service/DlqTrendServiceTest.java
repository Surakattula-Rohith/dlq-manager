package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.enums.TrendRange;
import com.dlqmanager.repository.DlqCountSampleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DlqTrendServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 14, 37);
    private static final UUID TOPIC = UUID.randomUUID();

    private final DlqTopicRepository topics = mock(DlqTopicRepository.class);
    private final DlqCountSampleRepository samples = mock(DlqCountSampleRepository.class);
    private final DlqTrendService trendService = new DlqTrendService(topics, samples);

    @Test
    void last24HoursIsOnePointPerHourEndingWithTheCurrentHour() {
        List<DlqTrendService.TrendPoint> points = trend(TrendRange.LAST_24_HOURS, List.of(), null);

        assertThat(points).hasSize(24);
        assertThat(points.get(0).start()).isEqualTo(LocalDateTime.of(2026, 10, 4, 15, 0));
        assertThat(points.get(23).start()).isEqualTo(LocalDateTime.of(2026, 10, 5, 14, 0));
    }

    @Test
    void last7DaysIsOnePointPerSixHoursOnTheClock() {
        List<DlqTrendService.TrendPoint> points = trend(TrendRange.LAST_7_DAYS, List.of(), null);

        assertThat(points).hasSize(28);
        assertThat(points.get(27).start()).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
        assertThat(points.get(0).start()).isEqualTo(LocalDateTime.of(2026, 9, 28, 18, 0));
    }

    @Test
    void pendingIsTheLastValueAndNewMessagesIsTheGrowthInEachHour() {
        List<DlqCountSample> history = List.of(
                sample(12, 58, 39, 99),
                sample(13, 5, 40, 100),
                sample(13, 30, 46, 106),    // 6 new failures
                sample(13, 59, 50, 110),    // 4 more
                sample(14, 10, 20, 112),    // 2 new; 32 replayed -> pending drops
                sample(14, 36, 21, 113));   // 1 new

        List<DlqTrendService.TrendPoint> points = trend(TrendRange.LAST_24_HOURS, history, null);

        DlqTrendService.TrendPoint oneOClock = points.get(22);
        assertThat(oneOClock.pending()).isEqualTo(50L);
        assertThat(oneOClock.newMessages()).isEqualTo(11L);   // 99 before the hour -> 110 at its end

        DlqTrendService.TrendPoint twoOClock = points.get(23);
        assertThat(twoOClock.pending()).isEqualTo(21L);
        assertThat(twoOClock.newMessages()).isEqualTo(3L);    // 110 -> 113: replays don't count
    }

    @Test
    void theFirstHourCountsFromTheSampleJustBeforeTheRange() {
        // The range starts at 15:00 yesterday; the sample at 14:59 is the starting point
        DlqCountSample before = sample(LocalDateTime.of(2026, 10, 4, 14, 59), 8, 10);
        List<DlqCountSample> history = List.of(sample(LocalDateTime.of(2026, 10, 4, 15, 20), 13, 15));

        assertThat(trend(TrendRange.LAST_24_HOURS, history, before).get(0).newMessages()).isEqualTo(5L);
    }

    @Test
    void aSampleLongBeforeTheRangeIsNotUsedAsTheStartingPoint() {
        DlqCountSample before = sample(LocalDateTime.of(2026, 10, 4, 9, 0), 0, 0);
        List<DlqCountSample> history = List.of(
                sample(LocalDateTime.of(2026, 10, 4, 15, 20), 13, 15),
                sample(LocalDateTime.of(2026, 10, 4, 15, 40), 14, 16));

        assertThat(trend(TrendRange.LAST_24_HOURS, history, before).get(0).newMessages()).isEqualTo(1L);
    }

    @Test
    void hoursWithoutHistoryHaveNoValuesInsteadOfZero() {
        List<DlqTrendService.TrendPoint> points = trend(TrendRange.LAST_24_HOURS, List.of(sample(14, 1, 5, 5)), null);

        assertThat(points.subList(0, 23)).allSatisfy(point -> {
            assertThat(point.pending()).isNull();
            assertThat(point.newMessages()).isNull();
        });
        assertThat(points.get(23).pending()).isEqualTo(5L);
    }

    @Test
    void messagesThatArrivedWhileTheAppWasDownAreNotPiledIntoOneHour() {
        // Samples at 10:59, then nothing until 14:05 (app stopped); 300 arrived in between
        List<DlqCountSample> history = List.of(
                sample(10, 59, 10, 100),
                sample(14, 5, 310, 400),
                sample(14, 30, 312, 402));

        List<DlqTrendService.TrendPoint> points = trend(TrendRange.LAST_24_HOURS, history, null);

        assertThat(points.get(19).newMessages()).isZero();          // 10:00 - only one sample
        assertThat(points.get(20).newMessages()).isNull();          // 11:00 - gap
        assertThat(points.get(23).newMessages()).isEqualTo(2L);     // 14:00 - counted from its own first sample
    }

    @Test
    void aTopicCreatedAgainInKafkaNeverShowsANegativeCount() {
        List<DlqCountSample> history = List.of(sample(13, 50, 500, 500), sample(14, 10, 3, 3));

        assertThat(trend(TrendRange.LAST_24_HOURS, history, null).get(23).newMessages()).isZero();
    }

    @Test
    void unknownTopicIsReported() {
        when(topics.existsById(TOPIC)).thenReturn(false);

        assertThatThrownBy(() -> trendService.getTrend(TOPIC, TrendRange.LAST_24_HOURS, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bucketStartFollowsTheClock() {
        assertThat(DlqTrendService.bucketStart(NOW, TrendRange.LAST_24_HOURS)).isEqualTo(LocalDateTime.of(2026, 10, 5, 14, 0));
        assertThat(DlqTrendService.bucketStart(NOW, TrendRange.LAST_7_DAYS)).isEqualTo(LocalDateTime.of(2026, 10, 5, 12, 0));
        assertThat(DlqTrendService.bucketStart(LocalDateTime.of(2026, 10, 5, 5, 59), TrendRange.LAST_7_DAYS))
                .isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 0));
    }

    private List<DlqTrendService.TrendPoint> trend(TrendRange range, List<DlqCountSample> history, DlqCountSample before) {
        when(topics.existsById(TOPIC)).thenReturn(true);
        when(samples.findByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(eq(TOPIC), any()))
                .thenReturn(new ArrayList<>(history));
        when(samples.findFirstByDlqTopicIdAndSampledAtBeforeOrderBySampledAtDesc(eq(TOPIC), any()))
                .thenReturn(Optional.ofNullable(before));
        return trendService.getTrend(TOPIC, range, NOW);
    }

    /**
     * A sample taken today (5 Oct) at hour:minute
     */
    private static DlqCountSample sample(int hour, int minute, long pending, long endOffsetSum) {
        return sample(LocalDateTime.of(2026, 10, 5, hour, minute), pending, endOffsetSum);
    }

    private static DlqCountSample sample(LocalDateTime at, long pending, long endOffsetSum) {
        DlqCountSample sample = new DlqCountSample();
        sample.setDlqTopicId(TOPIC);
        sample.setSampledAt(at);
        sample.setPendingCount(pending);
        sample.setEndOffsetSum(endOffsetSum);
        return sample;
    }
}
