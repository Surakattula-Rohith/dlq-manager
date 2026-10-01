package com.dlqmanager.service;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.model.entity.ReplayClaim;
import com.dlqmanager.repository.ReplayClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayClaimIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ReplayClaimService replayClaimService;

    @Autowired
    private ReplayClaimRepository replayClaimRepository;

    @Test
    void onlyOneClaimPerMessageUntilReleased() {
        UUID topic = UUID.randomUUID();

        Optional<UUID> first = replayClaimService.tryClaim(topic, 0, 7L, "alice");
        assertThat(first).isPresent();
        assertThat(replayClaimService.tryClaim(topic, 0, 7L, "bob")).isEmpty();

        // Other messages of the same topic are not blocked
        assertThat(replayClaimService.tryClaim(topic, 0, 8L, "bob")).isPresent();
        assertThat(replayClaimService.tryClaim(topic, 1, 7L, "bob")).isPresent();

        replayClaimService.release(first.get());
        assertThat(replayClaimService.tryClaim(topic, 0, 7L, "bob")).isPresent();
    }

    @Test
    void claimLeftBehindByACrashIsTakenOver() {
        UUID topic = UUID.randomUUID();
        UUID claimId = replayClaimService.tryClaim(topic, 0, 3L, "alice").orElseThrow();

        // Pretend the replay holding it crashed long ago
        ReplayClaim claim = replayClaimRepository.findById(claimId).orElseThrow();
        claim.setClaimedAt(LocalDateTime.now().minus(ReplayClaimService.ABANDONED_AFTER).minusMinutes(1));
        replayClaimRepository.save(claim);

        assertThat(replayClaimService.tryClaim(topic, 0, 3L, "bob")).isPresent();
    }
}
