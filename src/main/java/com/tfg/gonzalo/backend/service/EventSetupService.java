package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.setup.AssignGroupPodRequest;
import com.tfg.gonzalo.backend.dto.setup.CreateGroupRequest;
import com.tfg.gonzalo.backend.dto.setup.CreatePodRequest;
import com.tfg.gonzalo.backend.dto.setup.EventStructureResponse;
import com.tfg.gonzalo.backend.dto.setup.GroupResponse;
import com.tfg.gonzalo.backend.dto.setup.PodResponse;
import com.tfg.gonzalo.backend.dto.setup.UpdateGroupRequest;
import com.tfg.gonzalo.backend.dto.setup.UpdatePodRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.Pod;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import com.tfg.gonzalo.backend.repository.PodRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class EventSetupService {

    private static final String LEGACY_UNASSIGNED_POD_NAME = "Sin asignar";
    private static final String DEFAULT_POD_NAME_PREFIX = "Pod";
    private static final Pattern DEFAULT_POD_NAME_PATTERN = Pattern.compile("^pod\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final EventRepository eventRepository;
    private final PodRepository podRepository;
    private final EventGroupRepository eventGroupRepository;
    private final GameStateRepository gameStateRepository;
    private final EventLogService eventLogService;
    private final CodeGeneratorService codeGeneratorService;
    private final VillainFormulaService villainFormulaService;
    private final AccessControlService accessControlService;

    public EventSetupService(EventRepository eventRepository,
                             PodRepository podRepository,
                             EventGroupRepository eventGroupRepository,
                             GameStateRepository gameStateRepository,
                             EventLogService eventLogService,
                             CodeGeneratorService codeGeneratorService,
                             VillainFormulaService villainFormulaService,
                             AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.podRepository = podRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.gameStateRepository = gameStateRepository;
        this.eventLogService = eventLogService;
        this.codeGeneratorService = codeGeneratorService;
        this.villainFormulaService = villainFormulaService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public PodResponse createPod(Long eventId, CreatePodRequest request) {
        Event event = findEditableEvent(eventId);
        List<Pod> currentPods = listRealPods(eventId);

        Pod pod = new Pod();
        pod.setEvent(event);
        pod.setName(resolvePodName(currentPods, request.name()));
        pod.setSortOrder(request.sortOrder() == null ? resolveNextSortOrder(currentPods) : request.sortOrder());
        pod = podRepository.save(pod);

        eventLogService.appendOrganizerLog(event, EventActionType.POD_CREATED,
                null, pod.getName(), "Pod creado para el evento.");
        return mapPodResponse(pod, List.of());
    }

    @Transactional
    public GroupResponse createGroup(Long eventId, Long podId, CreateGroupRequest request) {
        Event event = findEditableEvent(eventId);
        Pod pod = findPod(podId, eventId);

        if (eventGroupRepository.existsByEventIdAndIdentifierIgnoreCase(eventId, request.identifier().trim())) {
            throw new BusinessRuleException("Ya existe una mesa con ese identificador dentro del evento.");
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
        int currentGroupCount = eventGroupRepository.findByEventId(eventId).size();
        gameState.setSchemeThreatTarget(villainFormulaService.computeThreatTarget(
                event.getVillainThreatRuleTypeSnapshot(),
                event.getVillainThreatRuleValueSnapshot(),
                currentGroupCount
        ));
        gameStateRepository.save(gameState);

        eventLogService.appendOrganizerLog(event, EventActionType.GROUP_CREATED,
                null, group.getIdentifier(), "Mesa creada y amenaza objetivo recalculada.");
        return mapGroupResponse(group);
    }

    @Transactional
    public GroupResponse assignGroupToPod(Long eventId, Long groupId, AssignGroupPodRequest request) {
        Event event = findAssignableEvent(eventId);
        EventGroup group = eventGroupRepository.findByIdAndEventId(groupId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la mesa/jugador solicitado para el evento."));
        Pod targetPod = findPod(request.podId(), eventId);

        if (hasAssignedPod(group) && group.getPod().getId().equals(targetPod.getId())) {
            return mapGroupResponse(group);
        }

        String previousPodName = resolveCurrentPodName(group);
        group.setPod(targetPod);
        group = eventGroupRepository.save(group);

        eventLogService.appendOrganizerLog(event, group, EventActionType.GROUP_UPDATED,
                previousPodName, targetPod.getName(), "Jugador/mesa reasignado de pod.");

        return mapGroupResponse(group);
    }

    @Transactional
    public PodResponse updatePod(Long eventId, Long podId, UpdatePodRequest request) {
        Event event = findEditableEvent(eventId);
        Pod pod = findPod(podId, eventId);

        String previousName = pod.getName();
        String nextName = request.name().trim();
        validatePodName(nextName);
        pod.setName(nextName);
        pod = podRepository.save(pod);

        eventLogService.appendOrganizerLog(event, EventActionType.POD_UPDATED,
                previousName, nextName, "Nombre del pod actualizado.");
        return mapPodResponse(pod, List.of());
    }

    @Transactional
    public void deletePod(Long eventId, Long podId) {
        Event event = findEditableEvent(eventId);
        Pod pod = findPod(podId, eventId);

        List<EventGroup> groups = eventGroupRepository.findByPodId(podId);
        for (EventGroup group : groups) {
            group.setPod(null);
        }
        if (!groups.isEmpty()) {
            eventGroupRepository.saveAll(groups);
        }

        podRepository.delete(pod);

        eventLogService.appendOrganizerLog(event, EventActionType.POD_UPDATED,
                pod.getName(), null, "Pod eliminado; sus mesas quedan pendientes de asignación.");
    }

    @Transactional
    public GroupResponse updateGroup(Long eventId, Long groupId, UpdateGroupRequest request) {
        Event event = findEditableEvent(eventId);
        EventGroup group = eventGroupRepository.findByIdAndEventId(groupId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la mesa/jugador solicitado para el evento."));

        String nextIdentifier = request.identifier().trim();
        if (!group.getIdentifier().equalsIgnoreCase(nextIdentifier)
                && eventGroupRepository.existsByEventIdAndIdentifierIgnoreCase(eventId, nextIdentifier)) {
            throw new BusinessRuleException("Ya existe una mesa con ese identificador dentro del evento.");
        }

        String previousIdentifier = group.getIdentifier();
        group.setIdentifier(nextIdentifier);
        group = eventGroupRepository.save(group);

        eventLogService.appendOrganizerLog(event, group, EventActionType.GROUP_UPDATED,
                previousIdentifier, nextIdentifier, "Nombre de la mesa actualizado.");
        return mapGroupResponse(group);
    }

    @Transactional
    public EventStructureResponse getEventStructure(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireReadAccessToEvent(event);
        GameState gameState = findGameState(eventId);
        List<Pod> pods = listRealPods(eventId);
        List<EventGroup> groups = eventGroupRepository.findByEventId(eventId);
        List<EventGroup> assignedGroups = groups.stream()
                .filter(this::hasAssignedPod)
                .toList();
        List<EventGroup> unassignedGroups = groups.stream()
                .filter(group -> !hasAssignedPod(group))
                .sorted(Comparator.comparing(EventGroup::getIdentifier, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<PodResponse> podResponses = pods.stream()
                .map(pod -> mapPodResponse(pod, assignedGroups.stream()
                        .filter(group -> group.getPod().getId().equals(pod.getId()))
                        .sorted(Comparator.comparing(EventGroup::getIdentifier, String.CASE_INSENSITIVE_ORDER))
                        .toList()))
                .toList();

        int assignedPlayerCount = assignedGroups.stream().mapToInt(EventGroup::getPlayerCount).sum();
        int unassignedPlayerCount = unassignedGroups.stream().mapToInt(EventGroup::getPlayerCount).sum();

        return new EventStructureResponse(
                event.getId(),
                event.getName(),
                event.getConfiguredPlayerCount(),
                groups.size(),
                assignedPlayerCount,
                unassignedGroups.size(),
                unassignedPlayerCount,
                gameState.getSchemeThreatTarget(),
                resolveSuggestedPodName(pods),
                podResponses,
                unassignedGroups.stream().map(this::mapGroupResponse).toList()
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

    private Event findAssignableEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireOrganizerForEvent(event);
        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha finalizado y no admite cambios.");
        }
        return event;
    }

    private Pod findPod(Long podId, Long eventId) {
        Pod pod = podRepository.findById(podId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pod solicitado."));
        if (!pod.getEvent().getId().equals(eventId) || !isRealPod(pod)) {
            throw new BusinessRuleException("El pod no pertenece al evento indicado.");
        }
        return pod;
    }

    private GameState findGameState(Long eventId) {
        return gameStateRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe estado global para el evento solicitado."));
    }

    private List<Pod> listRealPods(Long eventId) {
        return podRepository.findByEventIdOrderBySortOrderAsc(eventId).stream()
                .filter(this::isRealPod)
                .toList();
    }

    private boolean isRealPod(Pod pod) {
        return pod != null && !LEGACY_UNASSIGNED_POD_NAME.equalsIgnoreCase(pod.getName());
    }

    private boolean hasAssignedPod(EventGroup group) {
        return group.getPod() != null && isRealPod(group.getPod());
    }

    private String resolveCurrentPodName(EventGroup group) {
        return hasAssignedPod(group) ? group.getPod().getName() : null;
    }

    private int resolveNextSortOrder(List<Pod> currentPods) {
        return currentPods.stream()
                .map(Pod::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private String resolvePodName(List<Pod> currentPods, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return resolveSuggestedPodName(currentPods);
        }

        String normalizedName = requestedName.trim();
        validatePodName(normalizedName);
        return normalizedName;
    }

    private void validatePodName(String podName) {
        if (LEGACY_UNASSIGNED_POD_NAME.equalsIgnoreCase(podName)) {
            throw new BusinessRuleException("El nombre 'Sin asignar' está reservado para mesas pendientes de asignación.");
        }
    }

    private String resolveSuggestedPodName(List<Pod> currentPods) {
        int nextOrdinal = 1;
        while (containsDefaultPodName(currentPods, nextOrdinal)) {
            nextOrdinal++;
        }
        return DEFAULT_POD_NAME_PREFIX + " " + nextOrdinal;
    }

    private boolean containsDefaultPodName(List<Pod> currentPods, int ordinal) {
        return currentPods.stream()
                .map(Pod::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .map(DEFAULT_POD_NAME_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .anyMatch(value -> value == ordinal);
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
                .filter(this::hasAssignedPod)
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
                hasAssignedPod(group) ? group.getPod().getId() : null,
                group.getIdentifier(),
                group.getPlayerCount(),
                group.getRepresentativeCode(),
                group.getObserverCode()
        );
    }

    private List<String> buildWarnings(List<EventGroup> groups, int totalPlayers) {
        List<String> warnings = new ArrayList<>();
        if (groups.size() > 4) {
            warnings.add("El pod supera la recomendación de 4 mesas.");
        }
        if (totalPlayers > 16) {
            warnings.add("El pod supera la recomendación de 16 jugadores.");
        }
        if (groups.size() > 1) {
            int maxPlayers = groups.stream().mapToInt(EventGroup::getPlayerCount).max().orElse(0);
            int minPlayers = groups.stream().mapToInt(EventGroup::getPlayerCount).min().orElse(0);
            if (maxPlayers - minPlayers > 1) {
                warnings.add("La diferencia de jugadores entre mesas del pod es superior a 1.");
            }
        }
        return warnings;
    }
}