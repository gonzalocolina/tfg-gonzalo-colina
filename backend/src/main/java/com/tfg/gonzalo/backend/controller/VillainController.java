package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.villain.CreateVillainRequest;
import com.tfg.gonzalo.backend.dto.villain.UpdateVillainRequest;
import com.tfg.gonzalo.backend.dto.villain.VillainResponse;
import com.tfg.gonzalo.backend.service.VillainService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/villains")
public class VillainController {

    private final VillainService villainService;

    public VillainController(VillainService villainService) {
        this.villainService = villainService;
    }

    @GetMapping
    public List<VillainResponse> listVillains() {
        return villainService.listAvailableVillains();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VillainResponse createVillain(@Valid @RequestBody CreateVillainRequest request) {
        return villainService.createVillain(request);
    }

    @PutMapping("/{villainId}")
    public VillainResponse updateVillain(@PathVariable Long villainId,
                                         @Valid @RequestBody UpdateVillainRequest request) {
        return villainService.updateVillain(villainId, request);
    }

    @DeleteMapping("/{villainId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVillain(@PathVariable Long villainId) {
        villainService.deleteVillain(villainId);
    }
}