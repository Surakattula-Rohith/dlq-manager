package com.dlqmanager.service;

import com.dlqmanager.model.entity.DlqCountSample;
import com.dlqmanager.model.enums.TrendRange;
import com.dlqmanager.repository.DlqCountSampleRepository;
import com.dlqmanager.repository.DlqTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DLQ Trend
 *
 * Answers "is this DLQ getting better or worse?" from the samples the scheduler stores
 * every minute (pending count and end-offset sum, see AlertEvaluatorService).
 *
 * The range is cut into equal buckets aligned to the viewer's clock (whole hours, or
 * 00/06/12/18h in their time zone - in India a UTC hour would start at :30).
 * For each bucket:
 * - pending     = pending count at the last sample in the bucket
 * - newMessages = how much the end-offset sum grew during the bucket, i.e. how many
 *                 messages failed into the DLQ in that time (replays don't lower it)
 * A bucket without samples (app stopped, topic added later) has no values, so the
 * chart shows a gap instead of a made-up zero.
 */
@Service
@RequiredArgsConstructor
public class DlqTrendService {

    private final DlqTopicRepository dlqTopicRepository;
    private final DlqCountSampleRepository dlqCountSampleRepository;

    /**
     * @param start       start of the bucket
     * @param pending     messages waiting at the end of the bucket (null = no data)
     * @param newMessages messages that arrived in the DLQ during the bucket (null = no data)
     */
    public record TrendPoint(LocalDateTime start, Long pending, Long newMessages) {
    }

    /**
     * @param utcOffsetMinutes the viewer's offset from UTC (e.g. 330 for India), so buckets
     *                         start on their whole hours
     * @throws IllegalArgumentException if the DLQ topic doesn't exist
     */
    public List<TrendPoint> getTrend(UUID dlqTopicId, TrendRange range, int utcOffsetMinutes) {
        return getTrend(dlqTopicId, range, LocalDateTime.now(), utcOffsetMinutes);
    }

    List<TrendPoint> getTrend(UUID dlqTopicId, TrendRange range, LocalDateTime now, int utcOffsetMinutes) {
        if (!dlqTopicRepository.existsById(dlqTopicId)) {
            throw new IllegalArgumentException("DLQ topic not found: " + dlqTopicId);
        }

        // Align in the viewer's time, then go back to the app's time (UTC) to read the samples
        LocalDateTime firstBucket = bucketStart(now.plusMinutes(utcOffsetMinutes), range)
                .minusMinutes((long) (range.buckets() - 1) * range.bucketMinutes())
                .minusMinutes(utcOffsetMinutes);
        List<DlqCountSample> samples = dlqCountSampleRepository
                .findByDlqTopicIdAndSampledAtGreaterThanEqualOrderBySampledAtAsc(dlqTopicId, firstBucket);

        // The last sample before the range tells how many messages there were when the first bucket began
        DlqCountSample before = dlqCountSampleRepository
                .findFirstByDlqTopicIdAndSampledAtBeforeOrderBySampledAtDesc(dlqTopicId, firstBucket)
                .filter(sample -> !sample.getSampledAt().isBefore(firstBucket.minusMinutes(range.bucketMinutes())))
                .orElse(null);

        return buildPoints(samples, before, firstBucket, range);
    }

    /**
     * @param samples     samples from firstBucket onwards, oldest first
     * @param before      the sample just before firstBucket, or null
     * @param firstBucket start of the first bucket
     */
    static List<TrendPoint> buildPoints(List<DlqCountSample> samples, DlqCountSample before,
                                        LocalDateTime firstBucket, TrendRange range) {
        List<TrendPoint> points = new ArrayList<>(range.buckets());
        Long previousEnd = before != null ? before.getEndOffsetSum() : null;
        int next = 0;

        for (int bucket = 0; bucket < range.buckets(); bucket++) {
            LocalDateTime start = firstBucket.plusMinutes((long) bucket * range.bucketMinutes());
            LocalDateTime end = start.plusMinutes(range.bucketMinutes());

            DlqCountSample first = null;
            DlqCountSample last = null;
            while (next < samples.size() && samples.get(next).getSampledAt().isBefore(end)) {
                if (first == null) {
                    first = samples.get(next);
                }
                last = samples.get(next);
                next++;
            }

            if (last == null) {
                points.add(new TrendPoint(start, null, null));
                // After a gap, arrivals during the gap are not counted into the next bucket
                previousEnd = null;
                continue;
            }

            long baseline = previousEnd != null ? previousEnd : first.getEndOffsetSum();
            // The sum only drops if the topic was deleted and created again: count nothing rather than a negative
            long newMessages = Math.max(0, last.getEndOffsetSum() - baseline);
            points.add(new TrendPoint(start, last.getPendingCount(), newMessages));
            previousEnd = last.getEndOffsetSum();
        }
        return points;
    }

    /**
     * Start of the bucket that contains the given time, e.g. 14:37 -> 14:00 (hourly) or 12:00 (6-hourly)
     */
    static LocalDateTime bucketStart(LocalDateTime time, TrendRange range) {
        LocalDateTime midnight = time.truncatedTo(ChronoUnit.DAYS);
        long minutes = Duration.between(midnight, time).toMinutes();
        return midnight.plusMinutes(minutes - minutes % range.bucketMinutes());
    }
}
