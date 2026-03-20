package com.dlqmanager.repository;

import com.dlqmanager.model.entity.AlertEvent;
import com.dlqmanager.model.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {
    List<AlertEvent> findAllByOrderByTriggeredAtDesc();
    List<AlertEvent> findByStatus(AlertStatus status);
    long countByStatus(AlertStatus status);
}
