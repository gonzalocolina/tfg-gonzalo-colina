package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.Event;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);

    boolean existsByEventCode(String eventCode);

    Optional<Event> findFirstByOrganizerId(Long organizerId);

    java.util.List<Event> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from Event event
            where event.id = :eventId
            """)
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);
}