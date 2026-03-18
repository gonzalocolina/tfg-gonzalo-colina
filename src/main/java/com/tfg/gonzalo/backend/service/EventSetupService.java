package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.setup.CreateGroupRequest;
import com.tfg.gonzalo.backend.dto.setup.CreatePodRequest;
import com.tfg.gonzalo.backend.dto.setup.EventStructureResponse;
import com.tfg.gonzalo.backend.dto.setup.GroupResponse;
import com.tfg.gonzalo.backend.dto.setup.PodResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.EventLog;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.Pod;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import com.tfg.gonzalo.backend.repository.PodRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventSetupService {

    private final EventRepository eventRepository;
    private final PodRepository podRepository;
    private final EventGroupRepository eventGroupRepository;
    private final GameStateRepository gameStateRepository;
    private final EventLogRepository eventLogRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AccessControlService accessControlService;

    public EventSetupService(EventRepository eventRepository,
                             PodRepository podRepository,
                             EventGroupRepository eventGroupRepository,
                             GameStateRepository gameStateRepository,
                             EventLogRepository eventLogRepository,
                             CodeGeneratorService codeGeneratorService,
                             AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.podRepository = podRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.gameStateRepository = gameStateRepository;
        this.eventLogRepository = eventLogRepository;
        this.codeGeneratorService = codeGeneratorService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public PodResponse createPod(Long eventId, CreatePodRequest request) {
        Event event = findEditableEvent(eventId);

        Pod pod = new Pod();
        pod.setEvent(event);
        pod.setName(request.name().trim());
        pod.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        pod = podRepository.save(pod);

        registerLog(event, EventActionType.POD_CREATED, pod.getName(), "Pod creado para el evento.");
        return mapPodResponse(pod, List.of());
    }

    @Transactional
    public GroupResponse createGroup(Long eventId, Long podId, CreateGroupRequest request) {
        Event event = findEditableEvent(eventId);
        Pod pod = findPod(podId, eventId);

        if (eventGroupRepository.existsByEventIdAndIdentifierIgnoreCase(eventId, request.identifier().trim())) {
            throw new BusinessRuleException("Ya existe un grupo con ese identificador dentro del evento.");
        }

        EventGroup group = new EventGroup();
        group.setEvent(event);
        group.setPod(pod);
        group.setIdentifier(request.identifier().trim());
        group.setPlayerCount(request.playerCount());
        group.setRepresentativeCode(generateUniqueGroupCode(true));
        group.setObserverCode(generateUniqueGroupCode(false));
        group = eventGroupRepository.save(group);

        GameState gameState = findGameState(eventId);
        gameState.setSchemeThreatTarget(eventGroupRepository.findByEventId(eventId).size() * 2);
        gameStateRepository.save(gameState);

        registerLog(event, EventActionType.GROUP_CREATED, group.getIdentifier(), "Grupo creado y amenaza objetivo recalculada.");
        return mapGroupResponse(group);
    }

    public EventStructureResponse getEventStructure(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireReadAccessToEvent(event);
        GameState gameState = findGameState(eventId);
        List<Pod> pods = podRepository.findByEventIdOrderBySortOrderAsc(eventId);
        List<EventGroup> groups = eventGroupRepository.findByEventId(eventId);

        List<PodResponse> podResponses = pods.stream()
                .map(pod -> mapPodResponse(pod, groups.stream()
                        .filter(group -> group.getPod().getId().equals(pod.getId()))
                        .sorted(Comparator.comparing(EventGroup::getIdentifier, String.CASE_INSENSITIVE_ORDER))
                        .toList()))
                .toList();

        int assignedPlayerCount = groups.stream().mapToInt(EventGroup::getPlayerCount).sum();
        return new EventStructureResponse(
                event.getId(),
                event.getName(),
                event.getConfiguredPlayerCount(),
                groups.size(),
                assignedPlayerCount,
                gameState.getSchemeThreatTarget(),
                podResponses
        );
    }

    private Event findEditableEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireOrganizerForEvent(event);
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya no admite cambios de configuración.");
        }
        return event;
    }

    private Pod findPod(Long podId, Long eventId) {
        Pod pod = podRepository.findById(podId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pod solicitado."));
        if (!pod.getEvent().getId().equals(eventId)) {
            throw new BusinessRuleException("El pod no pertenece al evento indicado.");
        }
        return pod;
    }

    private GameState findGameState(Long eventId) {
        return gameStateRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe estado global para el evento solicitado."));
    }

    private String generateUniqueGroupCode(boolean representative) {
        String code = codeGeneratorService.generateGroupCode();
        while (representative
                ? eventGroupRepository.findByRepresentativeCode(code).isPresent()
                : eventGroupRepository.findByObserverCode(code).isPresent()) {
            code = codeGeneratorService.generateGroupCode();
        }
        return code;
    }

    private PodResponse mapPodResponse(Pod pod, List<EventGroup> groups) {
        List<EventGroup> podGroups = groups.isEmpty()
                ? eventGroupRepository.findByPodId(pod.getId()).stream()
                    .sorted(Comparator.comparing(EventGroup::getIdentifier, String.CASE_INSENSITIVE_ORDER))
                    .toList()
                : groups;
        int totalPlayers = podGroups.stream().mapToInt(EventGroup::getPlayerCount).sum();
        List<String> warnings = buildWarnings(podGroups, totalPlayers);
        List<GroupResponse> groupResponses = podGroups.stream()
                .map(this::mapGroupResponse)
                .toList();

        return new PodResponse(
                pod.getId(),
                pod.getName(),
                pod.getSortOrder(),
                podGroups.size(),
                totalPlayers,
                warnings,
                groupResponses
        );
    }

    private GroupResponse mapGroupResponse(EventGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getPod().getId(),
                group.getIdentifier(),
                group.getPlayerCount(),
                group.getRepresentativeCode(),
                group.getObserverCode()
        );
    }

    private List<String> buildWarnings(List<EventGroup> groups, int totalPlayers) {
        List<String> warnings = new ArrayList<>();
        if (groups.size() > 4) {
            warnings.add("El pod supera la recomendación de 4 grupos.");
        }
        if (totalPlayers > 16) {
            warnings.add("El pod supera la recomendación de 16 jugadores.");
        }
        if (groups.size() > 1) {
            int maxPlayers = groups.stream().mapToInt(EventGroup::getPlayerCount).max().orElse(0);
            int minPlayers = groups.stream().mapToInt(EventGroup::getPlayerCount).min().orElse(0);
            if (maxPlayers - minPlayers > 1) {
                warnings.add("La diferencia de jugadores entre grupos del pod es superior a 1.");
            }
        }
        return warnings;
    }

    private void registerLog(Event event, EventActionType actionType, String newValue, String notes) {
        EventLog eventLog = new EventLog();
        eventLog.setEvent(event);
        eventLog.setActor(event.getOrganizer());
        eventLog.setActionType(actionType);
        eventLog.setNewValue(newValue);
        eventLog.setNotes(notes);
        eventLogRepository.save(eventLog);
    }
}