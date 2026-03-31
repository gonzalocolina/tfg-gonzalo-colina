package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.Pod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PodRepository extends JpaRepository<Pod, Long> {

    List<Pod> findByEventIdOrderBySortOrderAsc(Long eventId);

    Optional<Pod> findByEventIdAndNameIgnoreCase(Long eventId, String name);
}