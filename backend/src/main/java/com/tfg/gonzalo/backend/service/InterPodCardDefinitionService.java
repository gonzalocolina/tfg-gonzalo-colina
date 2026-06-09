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
import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InterPodCardDefinitionService {

    private final InterPodCardDefinitionRepository interPodCardDefinitionRepository;
    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final InterPodCardRepository interPodCardRepository;
    private final AccessControlService accessControlService;
    private final InterPodCardImageStorageService imageStorageService;

    public InterPodCardDefinitionService(InterPodCardDefinitionRepository interPodCardDefinitionRepository,
                                         AppUserRepository appUserRepository,
                                         EventRepository eventRepository,
                                         InterPodCardRepository interPodCardRepository,
                                         AccessControlService accessControlService,
                                         InterPodCardImageStorageService imageStorageService) {
        this.interPodCardDefinitionRepository = interPodCardDefinitionRepository;
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.interPodCardRepository = interPodCardRepository;
        this.accessControlService = accessControlService;
        this.imageStorageService = imageStorageService;
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

        return deduplicateDefinitions(definitions).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinitionResponse> listSelectedDefinitionsForEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireReadAccessToEvent(event);

        return listDefinitionsVisibleToOrganizer(event.getOrganizer().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InterPodCardDefinitionResponse createDefinition(CreateInterPodCardDefinitionRequest request,
                                                           MultipartFile image) {
        AppUser organizer = requireOrganizerUser();

        String name = request.name().trim();
        validateUniqueName(organizer.getId(), name, null);
        InterPodCardMetrics metrics = resolveMetrics(request.cardCategory(), request.defaultHpMax(), request.defaultThreatMax());

        InterPodCardDefinition definition = new InterPodCardDefinition();
        definition.setName(name);
        definition.setType(InterPodCardDefinitionType.CUSTOM);
        definition.setCardCategory(request.cardCategory());
        definition.setDefaultHpMax(metrics.hpMax());
        definition.setDefaultThreatMax(metrics.threatMax());
        definition.setDefaultStatus(request.defaultStatus() == null
                ? InterPodCardStatus.REVELADA
                : request.defaultStatus());
        definition.setOwner(organizer);

        definition = interPodCardDefinitionRepository.save(definition);

        replaceDefinitionImage(definition, image, request.imageUrl());
        definition = interPodCardDefinitionRepository.save(definition);

        return toResponse(definition);
    }

    @Transactional
    public InterPodCardDefinitionResponse updateDefinition(Long definitionId,
                                                           UpdateInterPodCardDefinitionRequest request,
                                                           MultipartFile image) {
        AppUser organizer = requireOrganizerUser();

        InterPodCardDefinition definition = interPodCardDefinitionRepository
                .findByIdAndOwnerId(definitionId, organizer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe una carta inter-pod personalizada con ese identificador."));

        String name = request.name().trim();
        validateUniqueName(organizer.getId(), name, definition.getId());
        InterPodCardMetrics metrics = resolveMetrics(request.cardCategory(), request.defaultHpMax(), request.defaultThreatMax());

        definition.setName(name);
        definition.setCardCategory(request.cardCategory());
        definition.setDefaultHpMax(metrics.hpMax());
        definition.setDefaultThreatMax(metrics.threatMax());
        definition.setDefaultStatus(request.defaultStatus() == null
                ? InterPodCardStatus.REVELADA
                : request.defaultStatus());

        replaceDefinitionImage(definition, image, request.imageUrl());

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

        if (interPodCardRepository.existsByInterPodCardDefinitionId(definitionId)) {
            throw new BusinessRuleException(
                    "No se puede eliminar una carta inter-pod que ya tiene instancias creadas.");
        }

        imageStorageService.delete(definition.getImagePath());
        interPodCardDefinitionRepository.delete(definition);
    }

    @Transactional(readOnly = true)
    public DefinitionImagePayload getImage(Long definitionId) {
        InterPodCardDefinition definition = interPodCardDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la carta inter-pod solicitada."));
        Resource resource = imageStorageService.loadAsResource(definition.getImagePath());
        return new DefinitionImagePayload(resource, definition.getImageContentType());
    }

    public record DefinitionImagePayload(Resource resource, String contentType) {
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinition> resolveSelectedDefinitionsForOrganizer(Long organizerId,
                                                                               List<Long> requestedDefinitionIds) {
        return resolveSelectedDefinitionsForOrganizer(organizerId, requestedDefinitionIds, true);
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinition> resolveSelectedDefinitionsForOrganizer(Long organizerId,
                                                                               List<Long> requestedDefinitionIds,
                                                                               boolean useDefaultSelectionWhenEmpty) {
        List<Long> normalizedIds = requestedDefinitionIds == null
                ? List.of()
            : requestedDefinitionIds.stream().filter(Objects::nonNull).distinct().toList();

        if (normalizedIds.isEmpty()) {
            if (!useDefaultSelectionWhenEmpty) {
                return List.of();
            }
            return deduplicateDefinitions(
                    interPodCardDefinitionRepository.findByTypeOrderByNameAsc(InterPodCardDefinitionType.PREDEFINED));
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
                                                            Long interPodCardDefinitionId) {
        List<InterPodCardDefinition> selectedDefinitions = listDefinitionsVisibleToOrganizer(event.getOrganizer().getId());
        if (selectedDefinitions.isEmpty()) {
            throw new BusinessRuleException(
                "No hay cartas inter-pod disponibles para este organizador.");
        }

        return selectedDefinitions.stream()
                .filter(definition -> definition.getId().equals(interPodCardDefinitionId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "La carta inter-pod indicada no está disponible para este evento."));
    }

    @Transactional(readOnly = true)
    public List<InterPodCardDefinition> listDefinitionsVisibleToOrganizer(Long organizerId) {
        List<InterPodCardDefinition> definitions = new ArrayList<>();
        definitions.addAll(interPodCardDefinitionRepository
                .findByTypeOrderByNameAsc(InterPodCardDefinitionType.PREDEFINED));
        if (organizerId != null) {
            definitions.addAll(interPodCardDefinitionRepository.findByOwnerIdOrderByNameAsc(organizerId));
        }
        return deduplicateDefinitions(definitions);
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
                definition.getDefaultThreatMax(),
                definition.getDefaultStatus(),
                imageStorageService.resolveDefinitionImageUrl(definition.getId(), definition.getImagePath()),
                definition.getOwner() == null ? null : definition.getOwner().getId(),
                definition.getOwner() == null ? null : definition.getOwner().getUsername(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }

    private void replaceDefinitionImage(InterPodCardDefinition definition,
                                        MultipartFile image,
                                        String imageUrl) {
        String externalImageUrl = imageStorageService.normalizeExternalUrl(imageUrl);
        boolean hasUploadedImage = image != null && !image.isEmpty();

        if (hasUploadedImage && externalImageUrl != null) {
            throw new BusinessRuleException(
                    "Debes indicar un archivo de imagen o una URL externa, pero no ambas opciones.");
        }

        if (!hasUploadedImage && externalImageUrl == null) {
            return;
        }

        imageStorageService.delete(definition.getImagePath());

        if (hasUploadedImage) {
            InterPodCardImageStorageService.StoredImage storedImage = imageStorageService.store(0L, definition.getId(), image);
            if (storedImage != null) {
                definition.setImagePath(storedImage.relativePath());
                definition.setImageContentType(storedImage.contentType());
            }
            return;
        }

        definition.setImagePath(externalImageUrl);
        definition.setImageContentType(imageStorageService.inferContentTypeFromLocation(externalImageUrl));
    }

    private InterPodCardMetrics resolveMetrics(InterPodCardCategory category,
                                               Integer requestedHpMax,
                                               Integer requestedThreatMax) {
        if (category == null) {
            throw new BusinessRuleException("Debes indicar una categoría válida para la carta inter-pod.");
        }

        return switch (category) {
            case ESBIRRO -> new InterPodCardMetrics(requirePositive(requestedHpMax,
                    "Los esbirros inter-pod deben tener HP inicial mayor que 0."), 0);
            case PLAN -> new InterPodCardMetrics(0, requirePositive(requestedThreatMax,
                    "Los planes inter-pod deben tener amenaza inicial mayor que 0."));
        };
    }

    private int requirePositive(Integer value, String message) {
        if (value == null || value < 1) {
            throw new BusinessRuleException(message);
        }
        return value;
    }

    private record InterPodCardMetrics(int hpMax, int threatMax) {
    }

    private List<InterPodCardDefinition> deduplicateDefinitions(List<InterPodCardDefinition> definitions) {
        LinkedHashMap<String, InterPodCardDefinition> uniqueDefinitions = new LinkedHashMap<>();

        definitions.stream()
                .sorted(Comparator.comparing(InterPodCardDefinition::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InterPodCardDefinition::getId))
                .forEach(definition -> uniqueDefinitions.putIfAbsent(buildDefinitionKey(definition), definition));

        return new ArrayList<>(uniqueDefinitions.values());
    }

    private String buildDefinitionKey(InterPodCardDefinition definition) {
        Long ownerId = definition.getOwner() == null ? null : definition.getOwner().getId();
        return definition.getType() + ":" + ownerId + ":" + normalizeCardName(definition.getName());
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
