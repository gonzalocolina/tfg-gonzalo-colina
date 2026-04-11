package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.CoOrganizerAssignment;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoOrganizerAssignmentRepository extends JpaRepository<CoOrganizerAssignment, Long> {

    long countByEventId(Long eventId);

    List<CoOrganizerAssignment> findByEventId(Long eventId);

    List<CoOrganizerAssignment> findByEventIdAndUserId(Long eventId, Long userId);

    boolean existsByEventIdAndUserIdAndResponsibility(Long eventId, Long userId,
                                                      CoOrganizerResponsibility responsibility);

    Optional<CoOrganizerAssignment> findByEventIdAndResponsibility(Long eventId,
                                                                   CoOrganizerResponsibility responsibility);
}