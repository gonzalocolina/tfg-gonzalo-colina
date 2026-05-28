package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.EventGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventGroupRepository extends JpaRepository<EventGroup, Long> {

    List<EventGroup> findByEventId(Long eventId);

    Optional<EventGroup> findByIdAndEventId(Long id, Long eventId);

    List<EventGroup> findByPodId(Long podId);

    long countByPodId(Long podId);

    List<EventGroup> findByEventIdAndPodIsNull(Long eventId);

    Optional<EventGroup> findByEventIdAndIdentifierIgnoreCase(Long eventId, String identifier);

    Optional<EventGroup> findByEventIdAndRepresentativeCode(Long eventId, String representativeCode);

    Optional<EventGroup> findByEventIdAndObserverCode(Long eventId, String observerCode);

    Optional<EventGroup> findByRepresentativeCode(String representativeCode);

    Optional<EventGroup> findByObserverCode(String observerCode);

    boolean existsByEventIdAndIdentifierIgnoreCase(Long eventId, String identifier);

    long countByEventId(Long eventId);

    long countByEventIdAndPodIsNull(Long eventId);

    @Query("select coalesce(sum(group.playerCount), 0) from EventGroup group where group.event.id = :eventId")
    Integer sumPlayerCountByEventId(@Param("eventId") Long eventId);
}