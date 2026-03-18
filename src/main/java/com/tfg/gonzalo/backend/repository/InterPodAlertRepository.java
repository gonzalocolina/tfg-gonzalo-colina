package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.InterPodAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterPodAlertRepository extends JpaRepository<InterPodAlert, Long> {

    List<InterPodAlert> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<InterPodAlert> findByPodIdOrderByCreatedAtDesc(Long podId);

    List<InterPodAlert> findByEventIdAndPodIdOrderByCreatedAtDesc(Long eventId, Long podId);
}