package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.EventGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventGroupRepository extends JpaRepository<EventGroup, Long> {

    List<EventGroup> findByEventId(Long eventId);

    List<EventGroup> findByPodId(Long podId);

    Optional<EventGroup> findByEventIdAndRepresentativeCode(Long eventId, String representativeCode);

    Optional<EventGroup> findByEventIdAndObserverCode(Long eventId, String observerCode);

    Optional<EventGroup> findByRepresentativeCode(String representativeCode);

    Optional<EventGroup> findByObserverCode(String observerCode);

    boolean existsByEventIdAndIdentifierIgnoreCase(Long eventId, String identifier);
}