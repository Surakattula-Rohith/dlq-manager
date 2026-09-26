package com.dlqmanager.repository;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    List<AlertEvent> findAllByOrderByTriggeredAtDesc();
    List<AlertEvent> findByStatus(AlertStatus status);
    long countByStatus(AlertStatus status);
    void deleteByAlertRuleId(UUID alertRuleId);

    // Is this rule currently snoozed? (a SNOOZED event whose snooze hasn't ended yet)
    boolean existsByAlertRuleIdAndStatusAndSnoozedUntilAfter(UUID alertRuleId, AlertStatus status, LocalDateTime time);

    // Snoozed events whose snooze time is over
    List<AlertEvent> findByStatusAndSnoozedUntilBefore(AlertStatus status, LocalDateTime time);
}
