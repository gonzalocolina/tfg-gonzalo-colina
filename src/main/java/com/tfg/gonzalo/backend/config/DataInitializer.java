package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.VillainDefinitionRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "Administrador";

    private final AppUserRepository appUserRepository;
    private final VillainDefinitionRepository villainDefinitionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedAdminPassword;

    public DataInitializer(AppUserRepository appUserRepository,
                           VillainDefinitionRepository villainDefinitionRepository,
                           @Value("${app.seed.admin.password:}") String seedAdminPassword,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.villainDefinitionRepository = villainDefinitionRepository;
        this.seedAdminPassword = seedAdminPassword;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void run(ApplicationArguments args) {
        try {
            String resolvedAdminPassword = resolveSeedAdminPassword();
            if (appUserRepository.existsByUsername(DEFAULT_ADMIN_USERNAME)) {
                AppUser existing = appUserRepository.findByUsername(DEFAULT_ADMIN_USERNAME)
                        .orElseThrow(() -> new IllegalStateException("Usuario administrador no encontrado."));
                existing.setPasswordHash(passwordEncoder.encode(resolvedAdminPassword));
                appUserRepository.save(existing);
            } else {
                AppUser admin = new AppUser();
                admin.setUsername(DEFAULT_ADMIN_USERNAME);
                admin.setDisplayName(DEFAULT_ADMIN_DISPLAY_NAME);
                admin.setPasswordHash(passwordEncoder.encode(resolvedAdminPassword));
                admin.setRole(GlobalRole.ORGANIZER);
                admin.setEnabled(true);
                appUserRepository.save(admin);
            }

            ensurePredefinedLokiVillain();
        } catch (DataIntegrityViolationException e) {
            log.warn("DataInitializer: seed data skipped due to constraint violation ({})", e.getMessage());
        }
    }

    private void ensurePredefinedLokiVillain() {
        boolean exists = villainDefinitionRepository.existsByTypeAndNameIgnoreCase(
                VillainDefinitionType.PREDEFINED,
                "Loki"
        );
        if (exists) {
            return;
        }

        VillainDefinition loki = new VillainDefinition();
        loki.setName("Loki");
        loki.setType(VillainDefinitionType.PREDEFINED);
        loki.setHpRuleType(VillainRuleType.MULTIPLIER);
        loki.setHpRuleValue(20);
        loki.setThreatRuleType(VillainRuleType.MULTIPLIER);
        loki.setThreatRuleValue(2);
        loki.setOwner(null);
        villainDefinitionRepository.save(loki);
    }

    private String resolveSeedAdminPassword() {
        if (seedAdminPassword != null && !seedAdminPassword.isBlank()) {
            return seedAdminPassword;
        }
        String generated = UUID.randomUUID().toString();
        log.warn("No se ha definido app.seed.admin.password; se genera una contraseña temporal para el admin: {}",
                generated);
        return generated;
    }
}
