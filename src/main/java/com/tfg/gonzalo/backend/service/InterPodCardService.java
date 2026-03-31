package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CreateInterPodCardRequest;
import com.tfg.gonzalo.backend.dto.event.InterPodCardResponse;
import com.tfg.gonzalo.backend.dto.event.ReportInterPodCardDamageRequest;
import com.tfg.gonzalo.backend.dto.event.UpdateInterPodCardStatusRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.InterPodCard;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InterPodCardService {

    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final InterPodCardRepository interPodCardRepository;
    private final EventLogService eventLogService;
    private final AccessControlService accessControlService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final InterPodCardImageStorageService imageStorageService;
    private final InterPodCardDefinitionService interPodCardDefinitionService;

    public InterPodCardService(EventRepository eventRepository,
                               EventGroupRepository eventGroupRepository,
                               InterPodCardRepository interPodCardRepository,
                               EventLogService eventLogService,
                               AccessControlService accessControlService,
                               RealtimeEventPublisher realtimeEventPublisher,
                               InterPodCardImageStorageService imageStorageService,
                               InterPodCardDefinitionService interPodCardDefinitionService) {
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.interPodCardRepository = interPodCardRepository;
        this.eventLogService = eventLogService;
        this.accessControlService = accessControlService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.imageStorageService = imageStorageService;
        this.interPodCardDefinitionService = interPodCardDefinitionService;
    }

    @Transactional
    public InterPodCardResponse createCard(Long eventId,
                                           CreateInterPodCardRequest request,
                                           MultipartFile image) {
        Event event = findEvent(eventId);
        ensureEventAcceptsChanges(event);

        String representativeCode = request.representativeCode().trim();
        EventGroup sourceGroup = findRepresentativeGroup(eventId, representativeCode);
        accessControlService.requireRepresentativeOrOrganizerForGroup(event, sourceGroup, representativeCode);
        InterPodCardDefinition selectedDefinition = interPodCardDefinitionService.resolveDefinitionForEvent(
            event,
            request.interPodCardDefinitionId(),
            request.cardName()
        );
        int hpMax = request.hpMax() == null ? selectedDefinition.getDefaultHpMax() : request.hpMax();
        InterPodCardStatus initialStatus = request.status() == null
            ? selectedDefinition.getDefaultStatus()
            : request.status();

        InterPodCard card = new InterPodCard();
        card.setEvent(event);
        card.setPod(sourceGroup.getPod());
        card.setSourceGroup(sourceGroup);
        card.setInterPodCardDefinition(selectedDefinition);
        card.setCardName(selectedDefinition.getName());
        card.setCardCategory(selectedDefinition.getCardCategory());
        card.setHpMax(hpMax);
        card.setHpCurrent(hpMax);
        card.setStatus(initialStatus);
        card = interPodCardRepository.save(card);

        InterPodCardImageStorageService.StoredImage storedImage = imageStorageService.store(eventId, card.getId(), image);
        if (storedImage != null) {
            card.setImagePath(storedImage.relativePath());
            card.setImageContentType(storedImage.contentType());
            card = interPodCardRepository.save(card);
        }

        eventLogService.appendOrganizerLog(event, sourceGroup, EventActionType.INTER_POD_CARD_CREATED,
            null, card.getCardName(), "Carta inter-pod registrada para el pod.");

        publishCardRealtime(eventId, "INTER_POD_CARD_CREATED");

        return toResponse(card);
    }

    @Transactional
    public InterPodCardResponse reportDamage(Long eventId,
                                             Long cardId,
                                             ReportInterPodCardDamageRequest request) {
        Event event = findEvent(eventId);
        ensureEventAcceptsChanges(event);
        InterPodCard card = findCard(eventId, cardId);

        EventGroup actingGroup = authorizeMutation(event, card, request.representativeCode());

        int previousHp = card.getHpCurrent();
        int updatedHp = Math.max(0, previousHp - request.damage());
        card.setHpCurrent(updatedHp);
        if (updatedHp == 0) {
            card.setStatus(InterPodCardStatus.DERROTADA);
        }
        card = interPodCardRepository.save(card);

        eventLogService.appendOrganizerLog(event,
            resolveLogGroup(actingGroup, card),
            EventActionType.INTER_POD_CARD_DAMAGED,
            String.valueOf(previousHp),
            String.valueOf(updatedHp),
            "Daño aplicado sobre carta inter-pod.");

        publishCardRealtime(eventId, "INTER_POD_CARD_DAMAGED");

        return toResponse(card);
    }

    @Transactional
    public InterPodCardResponse updateStatus(Long eventId,
                                             Long cardId,
                                             UpdateInterPodCardStatusRequest request) {
        Event event = findEvent(eventId);
        ensureEventAcceptsChanges(event);
        InterPodCard card = findCard(eventId, cardId);

        EventGroup actingGroup = authorizeMutation(event, card, request.representativeCode());

        InterPodCardStatus previousStatus = card.getStatus();
        card.setStatus(request.status());
        if (request.status() == InterPodCardStatus.DERROTADA) {
            card.setHpCurrent(0);
        }
        card = interPodCardRepository.save(card);

        eventLogService.appendOrganizerLog(event,
            resolveLogGroup(actingGroup, card),
            EventActionType.INTER_POD_CARD_STATUS_UPDATED,
            previousStatus.name(),
            card.getStatus().name(),
            "Estado de carta inter-pod actualizado.");

        publishCardRealtime(eventId, "INTER_POD_CARD_STATUS_UPDATED");

        return toResponse(card);
    }

    @Transactional
    public void deleteCard(Long eventId, Long cardId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerOrCoOrganizerForEvent(event);

        InterPodCard card = findCard(eventId, cardId);
        imageStorageService.delete(card.getImagePath());
        interPodCardRepository.delete(card);

        eventLogService.appendOrganizerLog(event, card.getSourceGroup(), EventActionType.INTER_POD_CARD_DELETED,
            card.getCardName(), null, "Carta inter-pod eliminada.");

        publishCardRealtime(eventId, "INTER_POD_CARD_DELETED");
    }

    @Transactional
    public List<InterPodCardResponse> listCards(Long eventId, Long podId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);

        List<InterPodCard> cards = podId == null
                ? interPodCardRepository.findByEventIdOrderByCreatedAtDesc(eventId)
                : interPodCardRepository.findByEventIdAndPodIdOrderByCreatedAtDesc(eventId, podId);
        return cards.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CardImagePayload getImage(Long eventId, Long cardId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        InterPodCard card = findCard(eventId, cardId);
        Resource resource = imageStorageService.loadAsResource(card.getImagePath());
        return new CardImagePayload(resource, card.getImageContentType());
    }

    @Transactional
    public void deleteCardsForEvent(Long eventId) {
        List<InterPodCard> cards = interPodCardRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        cards.forEach(card -> imageStorageService.delete(card.getImagePath()));
        interPodCardRepository.deleteAll(cards);
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private InterPodCard findCard(Long eventId, Long cardId) {
        return interPodCardRepository.findByIdAndEventId(cardId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la carta inter-pod solicitada para el evento."));
    }

    private void ensureEventAcceptsChanges(Event event) {
        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("La partida ya ha terminado y no admite más cambios.");
        }
    }

    private EventGroup findRepresentativeGroup(Long eventId, String representativeCode) {
        return eventGroupRepository.findByEventIdAndRepresentativeCode(eventId, representativeCode)
                .orElseThrow(() -> new BusinessRuleException("Código de representante inválido para el evento."));
    }

    private EventGroup resolveLogGroup(EventGroup actingGroup, InterPodCard card) {
        return actingGroup == null ? card.getSourceGroup() : actingGroup;
    }

    private void publishCardRealtime(Long eventId, String operation) {
        realtimeEventPublisher.publishGameStateUpdate(eventId, operation, null, List.of(operation));
    }

    private EventGroup authorizeMutation(Event event, InterPodCard card, String representativeCode) {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.isOrganizer() || principal.isCoOrganizer()) {
            accessControlService.requireOrganizerOrCoOrganizerForEvent(event);
            return null;
        }

        if (!principal.isRepresentative()) {
            throw new ForbiddenOperationException(
                    "Solo el organizador/co-organizador o representantes del mismo pod pueden modificar esta carta.");
        }
        if (representativeCode == null || representativeCode.isBlank()) {
            throw new BusinessRuleException("El código de representante es obligatorio para esta operación.");
        }
        String normalizedCode = representativeCode.trim();

        EventGroup representativeGroup = findRepresentativeGroup(event.getId(), normalizedCode);

        accessControlService.requireRepresentativeOrOrganizerForGroup(event, representativeGroup, normalizedCode);

        if (!representativeGroup.getPod().getId().equals(card.getPod().getId())) {
            throw new ForbiddenOperationException(
                    "La carta inter-pod solo puede ser modificada por representantes del mismo pod.");
        }

        return representativeGroup;
    }

    private InterPodCardResponse toResponse(InterPodCard card) {
        return new InterPodCardResponse(
                card.getId(),
                card.getEvent().getId(),
                card.getPod().getId(),
                card.getPod().getName(),
                card.getSourceGroup().getId(),
                card.getSourceGroup().getIdentifier(),
                card.getInterPodCardDefinition() == null ? null : card.getInterPodCardDefinition().getId(),
                card.getCardName(),
                card.getCardCategory(),
                card.getHpMax(),
                card.getHpCurrent(),
                card.getStatus(),
                card.getImagePath() == null ? null
                        : "/api/events/%d/inter-pod-cards/%d/image".formatted(card.getEvent().getId(), card.getId()),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }

    public record CardImagePayload(Resource resource, String contentType) {
    }
}