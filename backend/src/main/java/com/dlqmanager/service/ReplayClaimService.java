package com.dlqmanager.service;

import com.dlqmanager.repository.ReplayClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes sure only one replay works on a DLQ message at a time
 *
 * Usage:
 *   claim the message -> check it wasn't already replayed -> send -> record the result -> release
 *
 * Because the "already replayed?" check happens while holding the claim, a second replay
 * of the same message either waits its turn (and then sees the first one's result) or is refused.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayClaimService {

    /**
     * A replay of one message takes seconds. A claim this old was left behind by a crash.
     */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);

    private final ReplayClaimRepository replayClaimRepository;

    /**
     * @return the claim id, or empty if someone else is replaying this message right now
     */
    public Optional<UUID> tryClaim(UUID dlqTopicId, int partition, long offset, String claimedBy) {
        LocalDateTime now = LocalDateTime.now();

        int abandoned = replayClaimRepository.deleteAbandoned(dlqTopicId, partition, offset, now.minus(ABANDONED_AFTER));
        if (abandoned > 0) {
            log.warn("Took over an abandoned replay claim: topic={}, partition={}, offset={}", dlqTopicId, partition, offset);
        }

        UUID claimId = UUID.randomUUID();
        int inserted = replayClaimRepository.tryInsert(claimId, dlqTopicId, partition, offset, claimedBy, now);
        return inserted == 1 ? Optional.of(claimId) : Optional.empty();
    }

    public void release(UUID claimId) {
        replayClaimRepository.deleteById(claimId);
    }
}
