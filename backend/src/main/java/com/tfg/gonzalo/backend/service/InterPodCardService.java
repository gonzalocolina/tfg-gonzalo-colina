package com.tfg.gonzalo.backend.service;



import com.tfg.gonzalo.backend.dto.event.CreateInterPodCardRequest;

import com.tfg.gonzalo.backend.dto.event.InterPodCardResponse;

import com.tfg.gonzalo.backend.dto.event.ReportInterPodCardProgressRequest;

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

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;

import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;

import com.tfg.gonzalo.backend.model.enums.TimerStatus;

import com.tfg.gonzalo.backend.model.enums.VillainRuleType;

import com.tfg.gonzalo.backend.repository.EventGroupRepository;

import com.tfg.gonzalo.backend.repository.EventRepository;

import com.tfg.gonzalo.backend.repository.InterPodCardRepository;

import com.tfg.gonzalo.backend.security.AccessControlService;

import com.tfg.gonzalo.backend.security.AuthPrincipal;

import jakarta.transaction.Transactional;

import java.util.List;

import org.springframework.core.io.Resource;

import org.springframework.stereotype.Service;

import org.springframework.transaction.support.TransactionSynchronization;

import org.springframework.transaction.support.TransactionSynchronizationManager;

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



        EventGroup sourceGroup = resolveSourceGroup(event, request.sourceGroupId());

        accessControlService.requireRepresentativeOrOrganizerForGroup(event, sourceGroup);

        ensureRepresentativeCanAct(event);

        ensureGroupHasAssignedPod(sourceGroup);

        InterPodCardDefinition selectedDefinition = interPodCardDefinitionService.resolveDefinitionForEvent(

            event,

            request.interPodCardDefinitionId()

        );

        ensureGroupHasNotReportedDefinition(eventId, sourceGroup, selectedDefinition);

        int tableCountInPod = resolveTableCountInPod(sourceGroup);

        InterPodCardStatus initialStatus = request.status() == null
            ? InterPodCardStatus.REVELADA
            : request.status();



        InterPodCard card = new InterPodCard();

        card.setEvent(event);

        card.setPod(sourceGroup.getPod());

        card.setSourceGroup(sourceGroup);

        card.setInterPodCardDefinition(selectedDefinition);

        card.setCardName(selectedDefinition.getName());

        card.setCardCategory(selectedDefinition.getCardCategory());

        card.setMetricMax(resolveMetricMax(selectedDefinition, request, tableCountInPod));

        initializeProgress(card, initialStatus);

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



        InterPodCardResponse response = toResponse(card);

        publishCardRealtime(eventId, card.getPod().getId(), "INTER_POD_CARD_CREATED", response, null);



        return response;

    }



    @Transactional

    public InterPodCardResponse reportProgress(Long eventId,

                                               Long cardId,

                                               ReportInterPodCardProgressRequest request) {

        Event event = findEvent(eventId);

        ensureEventAcceptsChanges(event);

        InterPodCard card = findCard(eventId, cardId);

        ensureCardCanReceiveProgress(card);



        EventGroup actingGroup = authorizeMutation(event, card);



        int previousMetric = card.getMetricCurrent();

        int updatedMetric = Math.max(0, previousMetric - request.amount());

        card.setMetricCurrent(updatedMetric);

        if (updatedMetric == 0) {

            card.setStatus(InterPodCardStatus.DERROTADA);

        }

        card = interPodCardRepository.save(card);



        if (card.getCardCategory() == InterPodCardCategory.ESBIRRO) {

            eventLogService.appendOrganizerLog(event,

                resolveLogGroup(actingGroup, card),

                EventActionType.INTER_POD_CARD_DAMAGED,

                String.valueOf(previousMetric),

                String.valueOf(updatedMetric),

                "Daño aplicado sobre carta inter-pod.");

            publishCardRealtime(eventId, card.getPod().getId(), "INTER_POD_CARD_DAMAGED", toResponse(card), null);

        } else {

            eventLogService.appendOrganizerLog(event,

                resolveLogGroup(actingGroup, card),

                EventActionType.INTER_POD_CARD_THREAT_REDUCED,

                String.valueOf(previousMetric),

                String.valueOf(updatedMetric),

                "Amenaza retirada de plan inter-pod.");

            publishCardRealtime(eventId, card.getPod().getId(), "INTER_POD_CARD_THREAT_UPDATED", toResponse(card), null);

        }



        return toResponse(card);

    }



    @Transactional

    public InterPodCardResponse updateStatus(Long eventId,

                                             Long cardId,

                                             UpdateInterPodCardStatusRequest request) {

        Event event = findEvent(eventId);

        ensureEventAcceptsChanges(event);

        InterPodCard card = findCard(eventId, cardId);



        EventGroup actingGroup = authorizeMutation(event, card);



        InterPodCardStatus previousStatus = card.getStatus();

        card.setStatus(request.status());

        if (request.status() == InterPodCardStatus.DERROTADA) {

            card.setMetricCurrent(0);

        }

        card = interPodCardRepository.save(card);



        eventLogService.appendOrganizerLog(event,

            resolveLogGroup(actingGroup, card),

            EventActionType.INTER_POD_CARD_STATUS_UPDATED,

            previousStatus.name(),

            card.getStatus().name(),

            "Estado de carta inter-pod actualizado.");



        InterPodCardResponse response = toResponse(card);

        publishCardRealtime(eventId, card.getPod().getId(), "INTER_POD_CARD_STATUS_UPDATED", response, null);



        return response;

    }



    @Transactional

    public void deleteCard(Long eventId, Long cardId) {

        Event event = findEvent(eventId);

        accessControlService.requireOrganizerOrCoOrganizerForEvent(event);



        InterPodCard card = findCard(eventId, cardId);

        Long targetPodId = card.getPod().getId();

        Long deletedCardId = card.getId();

        imageStorageService.delete(card.getImagePath());

        interPodCardRepository.delete(card);



        eventLogService.appendOrganizerLog(event, card.getSourceGroup(), EventActionType.INTER_POD_CARD_DELETED,

            card.getCardName(), null, "Carta inter-pod eliminada.");



        publishCardRealtime(eventId, targetPodId, "INTER_POD_CARD_DELETED", null, deletedCardId);

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



    private EventGroup resolveSourceGroup(Event event, Long requestedSourceGroupId) {

        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();

        if (principal.isOrganizerLike()) {

            accessControlService.requireOrganizerOrCoOrganizerForEvent(event);

            if (requestedSourceGroupId == null) {

                throw new BusinessRuleException("Debes indicar la mesa de origen.");

            }

            return findGroup(event.getId(), requestedSourceGroupId);

        }



        if (principal.groupId() == null) {

            throw new BusinessRuleException("No se pudo identificar la mesa autenticada.");

        }

        EventGroup group = findGroup(event.getId(), principal.groupId());

        accessControlService.requireRepresentativeForGroup(event, group);

        return group;

    }



    private EventGroup findGroup(Long eventId, Long groupId) {

        return eventGroupRepository.findByIdAndEventId(groupId, eventId)

                .orElseThrow(() -> new BusinessRuleException("La mesa indicada no existe en el evento."));

    }



    private EventGroup resolveLogGroup(EventGroup actingGroup, InterPodCard card) {

        return actingGroup == null ? card.getSourceGroup() : actingGroup;

    }



    private void ensureRepresentativeCanAct(Event event) {

        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();

        if (!principal.isRepresentative()) {

            return;

        }



        if (event.getTimerStatus() == TimerStatus.PAUSED) {

            throw new BusinessRuleException("La partida está pausada. Espera a que el organizador la reanude.");

        }

        if (event.getTimerStatus() == TimerStatus.READY) {

            throw new BusinessRuleException("No puedes realizar acciones hasta que el organizador inicie el contador.");

        }

    }



    private void ensureGroupHasAssignedPod(EventGroup group) {

        if (group.getPod() == null) {

            throw new BusinessRuleException("Tu mesa todavía no está asignada a un pod.");

        }

    }



    private int resolveTableCountInPod(EventGroup sourceGroup) {

        long configuredTableCount = eventGroupRepository.countByPodId(sourceGroup.getPod().getId());

        return Math.max(1, Math.toIntExact(configuredTableCount));

    }



    private void ensureGroupHasNotReportedDefinition(Long eventId,

                                                     EventGroup sourceGroup,

                                                     InterPodCardDefinition definition) {

        if (interPodCardRepository.existsByEventIdAndSourceGroupIdAndInterPodCardDefinitionId(

                eventId,

                sourceGroup.getId(),

                definition.getId())) {

            throw new BusinessRuleException(

                    "Esta mesa ya ha indicado que le ha aparecido esa carta inter-pod durante la partida.");

        }

    }



    private int resolveMetricMax(InterPodCardDefinition definition,

                                 CreateInterPodCardRequest request,

                                 int tableCountInPod) {

        int baseMetricMax = request.metricMax() == null

                ? definition.getDefaultMetricMax()

                : request.metricMax();

        return resolveMetricValue(definition.getRuleType(), baseMetricMax, tableCountInPod);

    }



    private int resolveMetricValue(VillainRuleType ruleType, int baseValue, int tableCountInPod) {

        int safeBaseValue = Math.max(0, baseValue);

        if (ruleType == VillainRuleType.FIXED_VALUE) {

            return safeBaseValue;

        }

        return safeBaseValue * Math.max(0, tableCountInPod);

    }



    private void initializeProgress(InterPodCard card, InterPodCardStatus initialStatus) {

        boolean defeated = initialStatus == InterPodCardStatus.DERROTADA;

        card.setMetricCurrent(defeated ? 0 : card.getMetricMax());

    }



    private void ensureCardCanReceiveProgress(InterPodCard card) {

        if (card.getStatus() == InterPodCardStatus.DERROTADA || card.getMetricCurrent() <= 0) {

            throw new BusinessRuleException("La carta inter-pod ya está derrotada y no admite más cambios.");

        }

    }



    private void publishCardRealtime(Long eventId,

                                       Long targetPodId,

                                       String operation,

                                       InterPodCardResponse interPodCard,

                                       Long interPodCardId) {

        Runnable publish = () -> realtimeEventPublisher.publishGameStateUpdate(

                eventId,

                operation,

                null,

                List.of(operation),

                targetPodId,

                interPodCard,

                interPodCardId);



        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override

                public void afterCommit() {

                    publish.run();

                }

            });

            return;

        }



        publish.run();

    }



    private EventGroup authorizeMutation(Event event, InterPodCard card) {

        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();

        if (principal.isOrganizerLike()) {

            accessControlService.requireOrganizerOrCoOrganizerForEvent(event);

            return null;

        }



        if (!principal.isRepresentative()) {

            throw new ForbiddenOperationException(

                    "Solo el organizador/co-organizador o representantes del mismo pod pueden modificar esta carta.");

        }

        ensureRepresentativeCanAct(event);

        if (principal.groupId() == null) {

            throw new BusinessRuleException("No se pudo identificar la mesa autenticada.");

        }



        EventGroup representativeGroup = findGroup(event.getId(), principal.groupId());

        ensureGroupHasAssignedPod(representativeGroup);

        accessControlService.requireRepresentativeForGroup(event, representativeGroup);



        if (!representativeGroup.getPod().getId().equals(card.getPod().getId())) {

            throw new ForbiddenOperationException(

                    "La carta inter-pod solo puede ser modificada por representantes del mismo pod.");

        }



        return representativeGroup;

    }



    private InterPodCardResponse toResponse(InterPodCard card) {

        String imageUrl = null;

        if (card.getImagePath() != null) {

            imageUrl = imageStorageService.resolveCardImageUrl(card.getEvent().getId(), card.getId(), card.getImagePath());

        } else if (card.getInterPodCardDefinition() != null && card.getInterPodCardDefinition().getImagePath() != null) {

            imageUrl = imageStorageService.resolveDefinitionImageUrl(

                    card.getInterPodCardDefinition().getId(),

                    card.getInterPodCardDefinition().getImagePath());

        }



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

                card.getMetricMax(),

                card.getMetricCurrent(),

                card.getStatus(),

                imageUrl,

                card.getCreatedAt(),

                card.getUpdatedAt()

        );

    }



    public record CardImagePayload(Resource resource, String contentType) {

    }

}

