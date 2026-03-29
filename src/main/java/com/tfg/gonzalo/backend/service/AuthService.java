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
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.security.TokenService;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";

    private final AppUserRepository appUserRepository;
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final EventGroupRepository eventGroupRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(AppUserRepository appUserRepository,
                       CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                       EventGroupRepository eventGroupRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.appUserRepository = appUserRepository;
        this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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
        EventGroup group = eventGroupRepository.findByRepresentativeCode(code)
                .orElseGet(() -> eventGroupRepository.findByObserverCode(code)
                        .orElseThrow(() -> new BusinessRuleException("Código de acceso inválido.")));

        return toGroupAuthResponse(group, code);
    }

    private AuthTokenResponse toOrganizerAuthResponse(AppUser user, String token) {
        return new AuthTokenResponse(
                BEARER,
                token,
                tokenService.getExpirationSeconds(),
                "ORGANIZER",
                null,
                null,
                user.getUsername(),
                user.getDisplayName()
        );
    }

    private AuthTokenResponse toGroupAuthResponse(EventGroup group, String code) {
        boolean representative = group.getRepresentativeCode().equals(code);
        String token = representative ? tokenService.createRepresentativeToken(group) : tokenService.createObserverToken(group);
        return new AuthTokenResponse(
                BEARER,
                token,
                tokenService.getExpirationSeconds(),
                representative ? "GROUP_REPRESENTATIVE" : "GROUP_OBSERVER",
                group.getEvent().getId(),
                group.getId(),
                null,
                "Mesa " + group.getIdentifier()
        );
    }
}
