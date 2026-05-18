package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.GameState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameStateRepository extends JpaRepository<GameState, Long> {

    Optional<GameState> findByEventId(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select gameState
            from GameState gameState
            join fetch gameState.event event
            where event.id = :eventId
            """)
    Optional<GameState> findByEventIdForUpdate(@Param("eventId") Long eventId);
}