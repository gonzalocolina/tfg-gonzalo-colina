package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterPodCardDefinitionRepository extends JpaRepository<InterPodCardDefinition, Long> {

    List<InterPodCardDefinition> findByTypeOrderByNameAsc(InterPodCardDefinitionType type);

    List<InterPodCardDefinition> findByTypeAndNameIgnoreCaseOrderByIdAsc(InterPodCardDefinitionType type, String name);

    List<InterPodCardDefinition> findByOwnerIdOrderByNameAsc(Long ownerId);

    Optional<InterPodCardDefinition> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<InterPodCardDefinition> findByTypeAndNameIgnoreCase(InterPodCardDefinitionType type, String name);

    boolean existsByTypeAndNameIgnoreCase(InterPodCardDefinitionType type, String name);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
}
