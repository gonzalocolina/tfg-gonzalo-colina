package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.EventLog;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<EventLog> findByEventIdAndGroupIdOrderByCreatedAtDesc(Long eventId, Long groupId);

    List<EventLog> findByEventIdAndGroupIdAndActionTypeOrderByCreatedAtDesc(Long eventId,
                                                                             Long groupId,
                                                                             EventActionType actionType);

    List<EventLog> findByEventIdAndActionTypeOrderByCreatedAtDesc(Long eventId, EventActionType actionType);
}