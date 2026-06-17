package com.tfg.gonzalo.backend.service;



import com.tfg.gonzalo.backend.dto.auth.OrganizerLoginRequest;

import com.tfg.gonzalo.backend.dto.auth.AuthTokenResponse;

import com.tfg.gonzalo.backend.dto.auth.GroupLoginByCodeRequest;

import com.tfg.gonzalo.backend.dto.auth.GroupLoginRequest;

import com.tfg.gonzalo.backend.dto.auth.RegisterRequest;

import com.tfg.gonzalo.backend.exception.BusinessRuleException;

import com.tfg.gonzalo.backend.model.AppUser;

import com.tfg.gonzalo.backend.model.Event;

import com.tfg.gonzalo.backend.model.EventGroup;

import com.tfg.gonzalo.backend.model.enums.EventActionType;

import com.tfg.gonzalo.backend.model.enums.EventStatus;

import com.tfg.gonzalo.backend.repository.AppUserRepository;

import com.tfg.gonzalo.backend.repository.EventGroupRepository;

import com.tfg.gonzalo.backend.repository.EventRepository;

import java.util.Locale;

import com.tfg.gonzalo.backend.security.TokenService;
import jakarta.transaction.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;



@Service

public class AuthService {



    private static final String BEARER = "Bearer";

    private static final String DEFAULT_PLAYER_NAME_PREFIX = "Mesa";

    private static final int MAX_GROUP_IDENTIFIER_LENGTH = 40;

    private static final String STRUCTURE_UPDATED_OPERATION = "EVENT_STRUCTURE_UPDATED";



    private final AppUserRepository appUserRepository;

    private final EventGroupRepository eventGroupRepository;

    private final EventRepository eventRepository;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final EventLogService eventLogService;

    private final RealtimeEventPublisher realtimeEventPublisher;



    public AuthService(AppUserRepository appUserRepository,

                       EventGroupRepository eventGroupRepository,

                       EventRepository eventRepository,

                       PasswordEncoder passwordEncoder,

                       TokenService tokenService,

                       EventLogService eventLogService,

                       RealtimeEventPublisher realtimeEventPublisher) {

        this.appUserRepository = appUserRepository;

        this.eventGroupRepository = eventGroupRepository;

        this.eventRepository = eventRepository;

        this.passwordEncoder = passwordEncoder;

        this.tokenService = tokenService;

        this.eventLogService = eventLogService;

        this.realtimeEventPublisher = realtimeEventPublisher;

    }



    public AuthTokenResponse register(RegisterRequest request) {

        String normalizedUsername = normalizeUsername(request.username());

        String normalizedEmail = normalizeOptionalEmail(request.email());



        if (appUserRepository.existsByUsernameIgnoreCase(normalizedUsername)) {

            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario.");

        }



        if (normalizedEmail != null && appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {

            throw new BusinessRuleException("Ya existe una cuenta con ese email.");

        }



        AppUser user = new AppUser();

        user.setUsername(normalizedUsername);

        user.setEmail(normalizedEmail);

        user.setPasswordHash(passwordEncoder.encode(request.password()));

        user = appUserRepository.save(user);



        return toOrganizerAuthResponse(user, tokenService.createOrganizerToken(user));

    }



    public AuthTokenResponse loginOrganizer(OrganizerLoginRequest request) {

        AppUser user = appUserRepository.findByUsernameIgnoreCase(normalizeUsername(request.username()))

                .orElseThrow(() -> new BusinessRuleException("Credenciales inválidas."));



        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {

            throw new BusinessRuleException("Credenciales inválidas.");

        }



        return toOrganizerAuthResponse(user, tokenService.createOrganizerToken(user));

    }



    private static String normalizeUsername(String value) {

        return value == null ? null : value.trim();

    }



    private static String normalizeOptionalEmail(String value) {

        if (value == null || value.isBlank()) {

            return null;

        }

        return value.trim().toLowerCase(Locale.ROOT);

    }



    @Transactional
    public AuthTokenResponse loginGroup(GroupLoginRequest request) {

        EventGroup group = eventGroupRepository.findByEventIdAndIdentifierIgnoreCase(

                        request.eventId(),

                        request.identifier().trim())

                .orElseThrow(() -> new BusinessRuleException("No existe la mesa indicada en el evento."));

        if (group.getEvent().getStatus() == EventStatus.FINISHED) {

            throw new BusinessRuleException("El evento ya ha finalizado y no admite nuevas incorporaciones.");

        }

        return toGroupAuthResponse(group, Boolean.TRUE.equals(request.asObserver()));

    }



    @Transactional
    public AuthTokenResponse loginGroupByCode(GroupLoginByCodeRequest request) {

        String eventCode = request.code().trim();

        Event event = eventRepository.findByEventCode(eventCode)

                .orElseThrow(() -> new BusinessRuleException("Código de acceso inválido."));



        if (event.getStatus() == EventStatus.FINISHED) {

            throw new BusinessRuleException("El evento ya ha finalizado y no admite nuevas incorporaciones.");

        }



        String groupLabel = normalizeGroupLabel(request.groupLabel());

        if (groupLabel != null) {

            return eventGroupRepository.findByEventIdAndIdentifierIgnoreCase(event.getId(), groupLabel)

                    .map(group -> toGroupAuthResponse(group, Boolean.TRUE.equals(request.asObserver())))

                    .orElseGet(() -> {

                        ensureNewGroupsAllowed(event);

                        return toGroupAuthResponse(

                                createGroupForJoiningPlayer(event, groupLabel, request.playerCount()),

                                Boolean.TRUE.equals(request.asObserver()));

                    });

        }



        ensureNewGroupsAllowed(event);

        EventGroup joinedGroup = createGroupForJoiningPlayer(event, null, request.playerCount());

        return toGroupAuthResponse(joinedGroup, Boolean.TRUE.equals(request.asObserver()));

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

                null

        );

    }



    private AuthTokenResponse toGroupAuthResponse(EventGroup group, boolean asObserver) {

        String token = asObserver

                ? tokenService.createObserverToken(group)

                : tokenService.createRepresentativeToken(group);

        return new AuthTokenResponse(

                BEARER,

                token,

                tokenService.getExpirationSeconds(),

                asObserver ? "GROUP_OBSERVER" : "GROUP_REPRESENTATIVE",

                group.getEvent().getId(),

                group.getId(),

                group.getEvent().getEventCode(),

                null,

                group.getIdentifier()

        );

    }



    private void ensureNewGroupsAllowed(Event event) {

        if (event.getStatus() == EventStatus.IN_PROGRESS) {

            throw new BusinessRuleException("La partida ya ha comenzado y no admite nuevas mesas.");

        }

    }



    private EventGroup createGroupForJoiningPlayer(Event event, String requestedGroupLabel, Integer playerCount) {

        String uniqueIdentifier = resolveUniqueGroupIdentifier(event.getId(), requestedGroupLabel);



        EventGroup group = new EventGroup();

        group.setEvent(event);

        group.setPod(null);

        group.setIdentifier(uniqueIdentifier);

        group.setPlayerCount(playerCount != null && playerCount >= 1 ? playerCount : 1);

        group = eventGroupRepository.save(group);



        eventLogService.appendOrganizerLog(event, group, EventActionType.GROUP_CREATED,

                null, uniqueIdentifier, "Jugador unido al evento mediante código de evento.");

        realtimeEventPublisher.publishEventOperation(event.getId(), STRUCTURE_UPDATED_OPERATION);



        return group;

    }



    private String normalizeGroupLabel(String value) {

        if (value == null || value.isBlank()) {

            return null;

        }

        return value.trim();

    }



    private String resolveUniqueGroupIdentifier(Long eventId, String requestedGroupLabel) {

        String baseName = resolveBasePlayerName(eventId, requestedGroupLabel);

        String normalizedBase = baseName;

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



    private String resolveBasePlayerName(Long eventId, String requestedGroupLabel) {

        if (requestedGroupLabel != null && !requestedGroupLabel.isBlank()) {

            String trimmed = requestedGroupLabel.trim();

            if (trimmed.length() > MAX_GROUP_IDENTIFIER_LENGTH) {

                return trimmed.substring(0, MAX_GROUP_IDENTIFIER_LENGTH);

            }

            return trimmed;

        }

        long nextOrdinal = eventGroupRepository.countByEventId(eventId) + 1;

        return DEFAULT_PLAYER_NAME_PREFIX + " " + nextOrdinal;

    }

}

