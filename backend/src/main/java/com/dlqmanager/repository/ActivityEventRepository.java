package com.dlqmanager.repository;

import com.dlqmanager.model.entity.ActivityEvent;
import com.dlqmanager.model.enums.ActivityAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    /**
     * Activity filtered by user and/or action (null = no filter on that field)
     */
    @Query("SELECT e FROM ActivityEvent e "
            + "WHERE (:username IS NULL OR e.username = :username) "
            + "AND (:action IS NULL OR e.action = :action)")
    Page<ActivityEvent> search(@Param("username") String username,
                               @Param("action") ActivityAction action,
                               Pageable pageable);

    /**
     * Everyone who appears in the log, for the "who" filter
     */
    @Query("SELECT DISTINCT e.username FROM ActivityEvent e ORDER BY e.username")
    List<String> findUsernames();
}
