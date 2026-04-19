package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.CoOrganizerInvitation;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoOrganizerInvitationRepository extends JpaRepository<CoOrganizerInvitation, Long> {

    long countByEventIdAndStatus(Long eventId, CoOrganizerInvitationStatus status);

    boolean existsByEventIdAndInviteeIdAndStatus(Long eventId, Long inviteeId,
                                                 CoOrganizerInvitationStatus status);

    List<CoOrganizerInvitation> findByEventIdAndStatusOrderByCreatedAtDesc(Long eventId,
                                                                           CoOrganizerInvitationStatus status);

    List<CoOrganizerInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long inviteeId,
                                                                             CoOrganizerInvitationStatus status);

    Optional<CoOrganizerInvitation> findByIdAndEventId(Long id, Long eventId);

    Optional<CoOrganizerInvitation> findByIdAndInviteeId(Long id, Long inviteeId);

    void deleteByEventId(Long eventId);
}