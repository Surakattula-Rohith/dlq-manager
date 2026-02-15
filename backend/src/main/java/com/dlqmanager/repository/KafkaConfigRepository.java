package com.dlqmanager.repository;

import com.dlqmanager.model.entity.KafkaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KafkaConfigRepository extends JpaRepository<KafkaConfig, Long> {

    /**
     * Get the first (and only) config row.
     */
    Optional<KafkaConfig> findFirstByOrderByIdAsc();
}
