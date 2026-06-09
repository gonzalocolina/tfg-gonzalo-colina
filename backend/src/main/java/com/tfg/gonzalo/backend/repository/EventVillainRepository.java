package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.EventVillain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventVillainRepository extends JpaRepository<EventVillain, Long> {

    Optional<EventVillain> findByEventId(Long eventId);

    boolean existsByVillainDefinitionId(Long villainDefinitionId);
}