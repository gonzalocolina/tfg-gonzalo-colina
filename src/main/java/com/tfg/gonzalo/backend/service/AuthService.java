package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.auth.AdminLoginRequest;
import com.tfg.gonzalo.backend.dto.auth.AuthTokenResponse;
import com.tfg.gonzalo.backend.dto.auth.CoOrganizerLoginRequest;
import com.tfg.gonzalo.backend.dto.auth.GroupLoginByCodeRequest;
import com.tfg.gonzalo.backend.dto.auth.GroupLoginRequest;
import com.tfg.gonzalo.backend.dto.auth.RegisterRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerAssignment;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import com.tfg.gonzalo.backend.security.TokenService;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";
    private static final String DEFAULT_PLAYER_NAME_PREFIX = "Jugador";
    private static final int MAX_GROUP_IDENTIFIER_LENGTH = 40;

    private final AppUserRepository appUserRepository;
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final EventGroupRepository eventGroupRepository;
    private final EventRepository eventRepository;
    private final GameStateRepository gameStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CodeGeneratorService codeGeneratorService;
    private final EventLogService eventLogService;
    private final VillainFormulaService villainFormulaService;

    public AuthService(AppUserRepository appUserRepository,
                       CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                       EventGroupRepository eventGroupRepository,
                       EventRepository eventRepository,
                       GameStateRepository gameStateRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       CodeGeneratorService codeGeneratorService,
                       EventLogService eventLogService,
                       VillainFormulaService villainFormulaService) {
        this.appUserRepository = appUserRepository;
        this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.eventRepository = eventRepository;
        this.gameStateRepository = gameStateRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.codeGeneratorService = codeGeneratorService;
        this.eventLogService = eventLogService;
        this.villainFormulaService = villainFormulaService;
    }

    public AuthTokenResponse register(RegisterRequest request) {
        if (appUserRepository.existsByUsername(request.username().trim())) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario.");
        }

        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeOptional(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(GlobalRole.ORGANIZER);
        user = appUserRepository.save(user);

        return toOrganizerAuthResponse(user, tokenService.createOrganizerToken(user));
    }

    public AuthTokenResponse loginAdmin(AdminLoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas."));

        if (!user.isEnabled() || user.getRole() != GlobalRole.ORGANIZER) {
            throw new BusinessRuleException("El usuario no tiene permisos de organizador.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessRuleException("Credenciales inválidas.");
        }

        return toOrganizerAuthResponse(user, tokenService.createOrganizerToken(user));
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public AuthTokenResponse loginCoOrganizer(CoOrganizerLoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas."));

        if (!user.isEnabled() || user.getRole() != GlobalRole.CO_ORGANIZER) {
            throw new BusinessRuleException("El usuario no tiene rol de co-organizador.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessRuleException("Credenciales inválidas.");
        }

        var assignments = coOrganizerAssignmentRepository.findByEventId(request.eventId()).stream()
                .filter(assignment -> assignment.getUser().getId().equals(user.getId()))
                .toList();
        if (assignments.isEmpty()) {
            throw new BusinessRuleException("El co-organizador no está asignado al evento indicado.");
        }

        String responsibilities = assignments.stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));

        String token = tokenService.createCoOrganizerToken(user, request.eventId());
        return new AuthTokenResponse(
            BEARER,
                token,
                tokenService.getExpirationSeconds(),
                "CO_ORGANIZER",
                request.eventId(),
                null,
                null,
                user.getUsername(),
                user.getDisplayName() + " (" + responsibilities + ")"
        );
    }

    public AuthTokenResponse loginGroup(GroupLoginRequest request) {
        String code = request.code().trim();
        EventGroup group = eventGroupRepository.findByEventIdAndRepresentativeCode(request.eventId(), code)
                .orElseGet(() -> eventGroupRepository.findByEventIdAndObserverCode(request.eventId(), code)
                        .orElseThrow(() -> new BusinessRuleException("Código de acceso inválido para el evento.")));
        return toGroupAuthResponse(group, code);
    }

    public AuthTokenResponse loginGroupByCode(GroupLoginByCodeRequest request) {
        String code = request.code().trim();
        EventGroup existingGroup = eventGroupRepository.findByRepresentativeCode(code)
                .orElseGet(() -> eventGroupRepository.findByObserverCode(code).orElse(null));

        if (existingGroup != null) {
            return toGroupAuthResponse(existingGroup, code);
        }

        Event event = eventRepository.findByEventCode(code)
                .orElseThrow(() -> new BusinessRuleException("Código de acceso inválido."));

        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha finalizado y no admite nuevas incorporaciones.");
        }

        EventGroup joinedGroup = createGroupForJoiningPlayer(event, request.displayName(), request.playerCount());
        return toGroupAuthResponse(joinedGroup, joinedGroup.getRepresentativeCode(), joinedGroup.getIdentifier());
    }

    private AuthTokenResponse toOrganizerAuthResponse(AppUser user, String token) {
        return new AuthTokenResponse(
                BEARER,
                token,
                tokenService.getExpirationSeconds(),
                "ORGANIZER",
                null,
                null,
                null,
                user.getUsername(),
                user.getDisplayName()
        );
    }

    private AuthTokenResponse toGroupAuthResponse(EventGroup group, String code) {
        return toGroupAuthResponse(group, code, "Mesa " + group.getIdentifier());
    }

    private AuthTokenResponse toGroupAuthResponse(EventGroup group, String code, String displayName) {
        boolean representative = group.getRepresentativeCode().equals(code);
        String token = representative ? tokenService.createRepresentativeToken(group) : tokenService.createObserverToken(group);
        return new AuthTokenResponse(
                BEARER,
                token,
                tokenService.getExpirationSeconds(),
                representative ? "GROUP_REPRESENTATIVE" : "GROUP_OBSERVER",
                group.getEvent().getId(),
                group.getId(),
                code,
                null,
                displayName
        );
    }

    private EventGroup createGroupForJoiningPlayer(Event event, String requestedDisplayName, Integer playerCount) {
        String baseName = resolveBasePlayerName(event.getId(), requestedDisplayName);
        String uniqueIdentifier = resolveUniqueGroupIdentifier(event.getId(), baseName);

        EventGroup group = new EventGroup();
        group.setEvent(event);
        group.setPod(null);
        group.setIdentifier(uniqueIdentifier);
        group.setPlayerCount(playerCount != null && playerCount >= 1 ? playerCount : 1);
        group.setRepresentativeCode(generateUniqueGroupCode(true));
        group.setObserverCode(generateUniqueGroupCode(false));
        group = eventGroupRepository.save(group);

        recalculateThreatTarget(event);
        eventLogService.appendOrganizerLog(event, group, EventActionType.GROUP_CREATED,
                null, uniqueIdentifier, "Jugador unido al evento mediante código de evento.");

        return group;
    }

    private String resolveBasePlayerName(Long eventId, String requestedDisplayName) {
        if (requestedDisplayName == null || requestedDisplayName.isBlank()) {
            long nextOrdinal = eventGroupRepository.countByEventId(eventId) + 1;
            return DEFAULT_PLAYER_NAME_PREFIX + " " + nextOrdinal;
        }
        String trimmed = requestedDisplayName.trim();
        if (trimmed.length() > MAX_GROUP_IDENTIFIER_LENGTH) {
            return trimmed.substring(0, MAX_GROUP_IDENTIFIER_LENGTH);
        }
        return trimmed;
    }

    private String resolveUniqueGroupIdentifier(Long eventId, String baseName) {
        String normalizedBase = baseName;
        if (normalizedBase == null || normalizedBase.isBlank()) {
            normalizedBase = DEFAULT_PLAYER_NAME_PREFIX;
        }

        if (normalizedBase.length() > MAX_GROUP_IDENTIFIER_LENGTH) {
            normalizedBase = normalizedBase.substring(0, MAX_GROUP_IDENTIFIER_LENGTH);
        }

        String candidate = normalizedBase;
        int suffix = 2;
        while (eventGroupRepository.existsByEventIdAndIdentifierIgnoreCase(eventId, candidate)) {
            String suffixText = " " + suffix;
            int maxBaseLength = Math.max(1, MAX_GROUP_IDENTIFIER_LENGTH - suffixText.length());
            String cutBase = normalizedBase.length() > maxBaseLength
                    ? normalizedBase.substring(0, maxBaseLength)
                    : normalizedBase;
            candidate = cutBase + suffixText;
            suffix++;
        }
        return candidate;
    }

    private String generateUniqueGroupCode(boolean representative) {
        String generated = codeGeneratorService.generateGroupCode();
        while (representative
                ? eventGroupRepository.findByRepresentativeCode(generated).isPresent()
                : eventGroupRepository.findByObserverCode(generated).isPresent()) {
            generated = codeGeneratorService.generateGroupCode();
        }
        return generated;
    }

    private void recalculateThreatTarget(Event event) {
        GameState gameState = gameStateRepository.findByEventId(event.getId()).orElse(null);
        if (gameState == null) {
            return;
        }
        int currentGroupCount = eventGroupRepository.findByEventId(event.getId()).size();
        gameState.setSchemeThreatTarget(villainFormulaService.computeThreatTarget(
                event.getVillainThreatRuleTypeSnapshot(),
                event.getVillainThreatRuleValueSnapshot(),
                currentGroupCount
        ));
        gameStateRepository.save(gameState);
    }
}
