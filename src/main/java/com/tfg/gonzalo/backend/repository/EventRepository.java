package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.Event;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByEventCode(String eventCode);

    boolean existsByEventCode(String eventCode);

    Optional<Event> findFirstByOrganizerId(Long organizerId);

    java.util.List<Event> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    boolean existsByVillainDefinitionId(Long villainDefinitionId);
}