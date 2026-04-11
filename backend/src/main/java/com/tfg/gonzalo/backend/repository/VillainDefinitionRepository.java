package com.tfg.gonzalo.backend.repository;

import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VillainDefinitionRepository extends JpaRepository<VillainDefinition, Long> {

    List<VillainDefinition> findByTypeOrderByNameAsc(VillainDefinitionType type);

    List<VillainDefinition> findByOwnerIdOrderByNameAsc(Long ownerId);

    Optional<VillainDefinition> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<VillainDefinition> findByTypeAndNameIgnoreCase(VillainDefinitionType type, String name);

    Optional<VillainDefinition> findFirstByTypeOrderByNameAsc(VillainDefinitionType type);

    boolean existsByTypeAndNameIgnoreCase(VillainDefinitionType type, String name);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
}