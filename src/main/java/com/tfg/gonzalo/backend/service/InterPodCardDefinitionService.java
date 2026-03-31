package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.interpod.CreateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.dto.interpod.UpdateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardDefinitionRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterPodCardDefinitionService {

    private final InterPodCardDefinitionRepository interPodCardDefinitionRepository;
    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final InterPodCardRepository interPodCardRepository;
    private final AccessControlService accessControlService;

    public InterPodCardDefinitionService(InterPodCardDefinitionRepository interPodCardDefinitionRepository,
                                         AppUserRepository appUserRepository,
                                         EventRepository eventRepository,
                                         InterPodCardRepository interPodCardRepository,
                                         AccessControlService accessControlService) {
        this.interPodCardDefinitionRepository = interPodCardDefinitionRepository;
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.interPodCardRepository = interPodCardRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinitionResponse> listAvailableDefinitions() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        List<InterPodCardDefinition> definitions = new ArrayList<>();
        definitions.addAll(interPodCardDefinitionRepository
                .findByTypeOrderByNameAsc(InterPodCardDefinitionType.PREDEFINED));
        if (principal.userId() != null) {
            definitions.addAll(interPodCardDefinitionRepository.findByOwnerIdOrderByNameAsc(principal.userId()));
        }

        return definitions.stream()
                .sorted(Comparator.comparing(InterPodCardDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinitionResponse> listSelectedDefinitionsForEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireReadAccessToEvent(event);

        return event.getInterPodCardDefinitions().stream()
                .sorted(Comparator.comparing(InterPodCardDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InterPodCardDefinitionResponse createDefinition(CreateInterPodCardDefinitionRequest request) {
        AppUser organizer = requireOrganizerUser();

        String name = request.name().trim();
        validateUniqueName(organizer.getId(), name, null);

        InterPodCardDefinition definition = new InterPodCardDefinition();
        definition.setName(name);
        definition.setType(InterPodCardDefinitionType.CUSTOM);
        definition.setCardCategory(request.cardCategory());
        definition.setDefaultHpMax(request.defaultHpMax());
        definition.setDefaultStatus(request.defaultStatus() == null
                ? InterPodCardStatus.REVELADA
                : request.defaultStatus());
        definition.setOwner(organizer);

        definition = interPodCardDefinitionRepository.save(definition);
        return toResponse(definition);
    }

    @Transactional
    public InterPodCardDefinitionResponse updateDefinition(Long definitionId,
                                                           UpdateInterPodCardDefinitionRequest request) {
        AppUser organizer = requireOrganizerUser();

        InterPodCardDefinition definition = interPodCardDefinitionRepository
                .findByIdAndOwnerId(definitionId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una carta inter-pod personalizada con ese identificador."));

        String name = request.name().trim();
        validateUniqueName(organizer.getId(), name, definition.getId());

        definition.setName(name);
        definition.setCardCategory(request.cardCategory());
        definition.setDefaultHpMax(request.defaultHpMax());
        definition.setDefaultStatus(request.defaultStatus() == null
                ? InterPodCardStatus.REVELADA
                : request.defaultStatus());

        definition = interPodCardDefinitionRepository.save(definition);
        return toResponse(definition);
    }

    @Transactional
    public void deleteDefinition(Long definitionId) {
        AppUser organizer = requireOrganizerUser();

        InterPodCardDefinition definition = interPodCardDefinitionRepository
                .findByIdAndOwnerId(definitionId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una carta inter-pod personalizada con ese identificador."));

        if (eventRepository.existsByInterPodCardDefinitionsId(definitionId)) {
            throw new BusinessRuleException(
                    "No se puede eliminar una carta inter-pod que está seleccionada en algún evento.");
        }
        if (interPodCardRepository.existsByInterPodCardDefinitionId(definitionId)) {
            throw new BusinessRuleException(
                    "No se puede eliminar una carta inter-pod que ya tiene instancias creadas.");
        }

        interPodCardDefinitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinition> resolveSelectedDefinitionsForOrganizer(Long organizerId,
                                                                               List<Long> requestedDefinitionIds) {
        List<Long> normalizedIds = requestedDefinitionIds == null
                ? List.of()
            : requestedDefinitionIds.stream().filter(Objects::nonNull).distinct().toList();

        if (normalizedIds.isEmpty()) {
            return interPodCardDefinitionRepository.findByTypeOrderByNameAsc(InterPodCardDefinitionType.PREDEFINED);
        }

        List<InterPodCardDefinition> selected = new ArrayList<>();
        for (Long definitionId : normalizedIds) {
            InterPodCardDefinition definition = interPodCardDefinitionRepository.findById(definitionId)
                    .orElseThrow(() -> new ResourceNotFoundException("No existe la carta inter-pod seleccionada."));
            ensureSelectableForOrganizer(definition, organizerId);
            selected.add(definition);
        }
        return selected;
    }

    @Transactional(readOnly = true)
    public InterPodCardDefinition resolveDefinitionForEvent(Event event,
                                                            Long interPodCardDefinitionId,
                                                            String legacyCardName) {
        List<InterPodCardDefinition> selectedDefinitions = event.getInterPodCardDefinitions()
            .stream()
            .toList();
        if (selectedDefinitions.isEmpty()) {
            throw new BusinessRuleException(
                    "El evento no tiene cartas inter-pod seleccionadas. Configúralas al crear el evento.");
        }

        if (interPodCardDefinitionId != null) {
            return selectedDefinitions.stream()
                    .filter(definition -> definition.getId().equals(interPodCardDefinitionId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "La carta inter-pod indicada no está seleccionada para este evento."));
        }

        if (legacyCardName != null && !legacyCardName.isBlank()) {
            String normalized = normalizeCardName(legacyCardName);
            return selectedDefinitions.stream()
                .filter(definition -> normalizeCardName(definition.getName()).equals(normalized))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "La carta inter-pod indicada no está seleccionada para este evento."));
        }

        throw new BusinessRuleException("Debes indicar una carta inter-pod para crear la instancia.");
    }

    private void ensureSelectableForOrganizer(InterPodCardDefinition definition, Long organizerId) {
        if (definition.getType() == InterPodCardDefinitionType.PREDEFINED) {
            return;
        }

        if (definition.getOwner() == null || !definition.getOwner().getId().equals(organizerId)) {
            throw new ForbiddenOperationException(
                    "No tienes permiso para seleccionar esa carta inter-pod personalizada.");
        }
    }

    private void validateUniqueName(Long ownerId, String name, Long currentDefinitionId) {
        boolean predefinedConflict = interPodCardDefinitionRepository
                .existsByTypeAndNameIgnoreCase(InterPodCardDefinitionType.PREDEFINED, name);
        if (predefinedConflict) {
            throw new BusinessRuleException("Ya existe una carta inter-pod con ese nombre.");
        }

        List<InterPodCardDefinition> ownerDefinitions = interPodCardDefinitionRepository.findByOwnerIdOrderByNameAsc(ownerId);
        boolean ownerConflict = ownerDefinitions.stream()
                .anyMatch(definition -> definition.getName().equalsIgnoreCase(name)
                        && (currentDefinitionId == null || !definition.getId().equals(currentDefinitionId)));

        if (ownerConflict) {
            throw new BusinessRuleException("Ya existe una carta inter-pod con ese nombre.");
        }
    }

    private AppUser requireOrganizerUser() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (!principal.isOrganizer() || principal.userId() == null) {
            throw new ForbiddenOperationException("Solo el organizador puede gestionar cartas inter-pod.");
        }
        return appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario organizador autenticado."));
    }

    private InterPodCardDefinitionResponse toResponse(InterPodCardDefinition definition) {
        return new InterPodCardDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                definition.getCardCategory(),
                definition.getDefaultHpMax(),
                definition.getDefaultStatus(),
                definition.getOwner() == null ? null : definition.getOwner().getId(),
                definition.getOwner() == null ? null : definition.getOwner().getUsername(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    private String normalizeCardName(String rawName) {
        return rawName == null
                ? ""
                : rawName.trim()
                    .replace("_", " ")
                    .replaceAll("[^\\p{L}\\p{N}]", "")
                    .toUpperCase();
    }
}
