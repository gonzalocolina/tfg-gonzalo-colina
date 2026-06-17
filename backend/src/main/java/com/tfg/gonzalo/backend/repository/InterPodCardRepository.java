package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.InterPodCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterPodCardRepository extends JpaRepository<InterPodCard, Long> {

    List<InterPodCard> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<InterPodCard> findByEventIdAndPodIdOrderByCreatedAtDesc(Long eventId, Long podId);

    List<InterPodCard> findByInterPodCardDefinitionId(Long interPodCardDefinitionId);

    Optional<InterPodCard> findByIdAndEventId(Long id, Long eventId);

    boolean existsByInterPodCardDefinitionId(Long interPodCardDefinitionId);

    boolean existsByEventIdAndSourceGroupIdAndInterPodCardDefinitionId(Long eventId,
                                                                       Long sourceGroupId,
                                                                       Long interPodCardDefinitionId);

    boolean existsBySourceGroupId(Long sourceGroupId);
}