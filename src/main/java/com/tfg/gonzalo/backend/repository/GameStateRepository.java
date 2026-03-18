package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.GameState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameStateRepository extends JpaRepository<GameState, Long> {

    Optional<GameState> findByEventId(Long eventId);
}