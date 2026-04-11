package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.villain.CreateVillainRequest;
import com.tfg.gonzalo.backend.dto.villain.UpdateVillainRequest;
import com.tfg.gonzalo.backend.dto.villain.VillainResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.VillainDefinitionRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VillainService {

    private final VillainDefinitionRepository villainDefinitionRepository;
    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final AccessControlService accessControlService;

    public VillainService(VillainDefinitionRepository villainDefinitionRepository,
                          AppUserRepository appUserRepository,
                          EventRepository eventRepository,
                          AccessControlService accessControlService) {
        this.villainDefinitionRepository = villainDefinitionRepository;
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public List<VillainResponse> listAvailableVillains() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        List<VillainDefinition> villains = new ArrayList<>();
        villains.addAll(villainDefinitionRepository.findByTypeOrderByNameAsc(VillainDefinitionType.PREDEFINED));
        if (principal.userId() != null) {
            villains.addAll(villainDefinitionRepository.findByOwnerIdOrderByNameAsc(principal.userId()));
        }
        return villains.stream()
                .sorted(Comparator.comparing(VillainDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VillainResponse createVillain(CreateVillainRequest request) {
        AppUser organizer = requireOrganizerUser();
        validateRuleValues(request.hpRuleValue(), request.threatRuleValue());

        String name = request.name().trim();
        if (villainDefinitionRepository.existsByOwnerIdAndNameIgnoreCase(organizer.getId(), name)
                || villainDefinitionRepository.existsByTypeAndNameIgnoreCase(VillainDefinitionType.PREDEFINED, name)) {
            throw new BusinessRuleException("Ya existe un villano con ese nombre.");
        }

        VillainDefinition villain = new VillainDefinition();
        villain.setName(name);
        villain.setType(VillainDefinitionType.CUSTOM);
        villain.setHpRuleType(request.hpRuleType());
        villain.setHpRuleValue(request.hpRuleValue());
        villain.setThreatRuleType(request.threatRuleType());
        villain.setThreatRuleValue(request.threatRuleValue());
        villain.setOwner(organizer);
        villain = villainDefinitionRepository.save(villain);

        return toResponse(villain);
    }

    @Transactional
    public VillainResponse updateVillain(Long villainId, UpdateVillainRequest request) {
        AppUser organizer = requireOrganizerUser();
        validateRuleValues(request.hpRuleValue(), request.threatRuleValue());

        VillainDefinition villain = villainDefinitionRepository.findByIdAndOwnerId(villainId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe un villano personalizado con ese identificador."));

        String name = request.name().trim();
        boolean nameChanged = !villain.getName().equalsIgnoreCase(name);
        if (nameChanged && (villainDefinitionRepository.existsByOwnerIdAndNameIgnoreCase(organizer.getId(), name)
                || villainDefinitionRepository.existsByTypeAndNameIgnoreCase(VillainDefinitionType.PREDEFINED, name))) {
            throw new BusinessRuleException("Ya existe un villano con ese nombre.");
        }

        villain.setName(name);
        villain.setHpRuleType(request.hpRuleType());
        villain.setHpRuleValue(request.hpRuleValue());
        villain.setThreatRuleType(request.threatRuleType());
        villain.setThreatRuleValue(request.threatRuleValue());
        villain = villainDefinitionRepository.save(villain);

        return toResponse(villain);
    }

    @Transactional
    public void deleteVillain(Long villainId) {
        AppUser organizer = requireOrganizerUser();
        VillainDefinition villain = villainDefinitionRepository.findByIdAndOwnerId(villainId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe un villano personalizado con ese identificador."));

        if (eventRepository.existsByVillainDefinitionId(villain.getId())) {
            throw new BusinessRuleException("No se puede eliminar un villano que ya está siendo usado por un evento.");
        }

        villainDefinitionRepository.delete(villain);
    }

    @Transactional
    public VillainDefinition findSelectableVillainForOrganizer(Long organizerId, Long villainDefinitionId) {
        if (villainDefinitionId == null) {
            return getDefaultPredefinedVillain();
        }

        VillainDefinition villain = villainDefinitionRepository.findById(villainDefinitionId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el villano seleccionado."));

        if (villain.getType() == VillainDefinitionType.PREDEFINED) {
            return villain;
        }

        if (villain.getOwner() == null || !villain.getOwner().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("No tienes permiso para utilizar ese villano personalizado.");
        }

        return villain;
    }

    private AppUser requireOrganizerUser() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (!principal.isOrganizer() || principal.userId() == null) {
            throw new ForbiddenOperationException("Solo el organizador puede gestionar villanos.");
        }
        return appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario organizador autenticado."));
    }

    private void validateRuleValues(Integer hpRuleValue, Integer threatRuleValue) {
        if (hpRuleValue == null || threatRuleValue == null) {
            throw new BusinessRuleException("Los valores de fórmula de HP y amenaza son obligatorios.");
        }
        if (hpRuleValue < 0 || threatRuleValue < 0) {
            throw new BusinessRuleException("Los valores de fórmula no pueden ser negativos.");
        }
    }

    private VillainResponse toResponse(VillainDefinition villain) {
        return new VillainResponse(
                villain.getId(),
                villain.getName(),
                villain.getType(),
                villain.getHpRuleType(),
                villain.getHpRuleValue(),
                villain.getThreatRuleType(),
                villain.getThreatRuleValue(),
                villain.getOwner() == null ? null : villain.getOwner().getId(),
                villain.getOwner() == null ? null : villain.getOwner().getUsername(),
                villain.getCreatedAt(),
                villain.getUpdatedAt()
        );
    }

    private VillainDefinition getDefaultPredefinedVillain() {
        return villainDefinitionRepository.findByTypeAndNameIgnoreCase(VillainDefinitionType.PREDEFINED, "Loki")
                .or(() -> villainDefinitionRepository.findFirstByTypeOrderByNameAsc(VillainDefinitionType.PREDEFINED))
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay un villano predefinido disponible. Contacta al administrador del sistema."));
    }
}