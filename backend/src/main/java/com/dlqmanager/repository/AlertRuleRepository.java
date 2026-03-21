package com.dlqmanager.repository;

import com.dlqmanager.model.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findByEnabledTrue();
    List<AlertRule> findByDlqTopicId(UUID dlqTopicId);
    void deleteByDlqTopicId(UUID dlqTopicId);
    List<AlertRule> findByNotificationChannelId(UUID notificationChannelId);
}
