package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.auth.OrganizerLoginRequest;
import com.tfg.gonzalo.backend.dto.auth.AuthTokenResponse;
import com.tfg.gonzalo.backend.dto.auth.GroupLoginByCodeRequest;
import com.tfg.gonzalo.backend.dto.auth.GroupLoginRequest;
import com.tfg.gonzalo.backend.dto.auth.RegisterRequest;
import com.tfg.gonzalo.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/organizer/login")
    public AuthTokenResponse loginOrganizer(@Valid @RequestBody OrganizerLoginRequest request) {
        return authService.loginOrganizer(request);
    }

    @PostMapping("/group/login")
    public AuthTokenResponse loginGroup(@Valid @RequestBody GroupLoginRequest request) {
        return authService.loginGroup(request);
    }

    @PostMapping("/group/login-by-code")
    public AuthTokenResponse loginGroupByCode(@Valid @RequestBody GroupLoginByCodeRequest request) {
        return authService.loginGroupByCode(request);
    }
}
