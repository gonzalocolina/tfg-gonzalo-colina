package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardDefinitionRepository;
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
    private static final String DEFAULT_ORGANIZER_USERNAME = "organizer";
    private static final int MANGOG_BASE_HP_PER_TABLE = 10;
    private static final int DOOR_BETWEEN_WORLDS_BASE_THREAT_PER_TABLE = 7;
    private static final String DEFAULT_PREDEFINED_CARD_IMAGE_CONTENT_TYPE = "image/jpeg";
    private static final String MANGOG_IMAGE_URL = "https://marvelcdb.com/bundles/cards/55041.jpg";
    private static final String DOOR_BETWEEN_WORLDS_IMAGE_URL = "https://marvelcdb.com/bundles/cards/55046.jpg";

    private final AppUserRepository appUserRepository;
    private final VillainDefinitionRepository villainDefinitionRepository;
    private final InterPodCardDefinitionRepository interPodCardDefinitionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedOrganizerPassword;

    public DataInitializer(AppUserRepository appUserRepository,
                           VillainDefinitionRepository villainDefinitionRepository,
                           InterPodCardDefinitionRepository interPodCardDefinitionRepository,
                           @Value("${app.seed.organizer.password:}") String seedOrganizerPassword,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.villainDefinitionRepository = villainDefinitionRepository;
        this.interPodCardDefinitionRepository = interPodCardDefinitionRepository;
        this.seedOrganizerPassword = seedOrganizerPassword;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void run(ApplicationArguments args) {
        try {
            String resolvedOrganizerPassword = resolveSeedOrganizerPassword();
            if (appUserRepository.existsByUsernameIgnoreCase(DEFAULT_ORGANIZER_USERNAME)) {
                AppUser existing = appUserRepository.findByUsernameIgnoreCase(DEFAULT_ORGANIZER_USERNAME)
                        .orElseThrow(() -> new IllegalStateException("Usuario organizador no encontrado."));
                existing.setPasswordHash(passwordEncoder.encode(resolvedOrganizerPassword));
                appUserRepository.save(existing);
            } else {
                AppUser organizer = new AppUser();
                organizer.setUsername(DEFAULT_ORGANIZER_USERNAME);
                organizer.setPasswordHash(passwordEncoder.encode(resolvedOrganizerPassword));
                appUserRepository.save(organizer);
            }

            ensurePredefinedLokiVillain();
            ensurePredefinedInterPodCards();
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

    private void ensurePredefinedInterPodCards() {
        upsertPredefinedInterPodCard(
                "THE_MANGOG",
                InterPodCardCategory.ESBIRRO,
                MANGOG_IMAGE_URL,
                MANGOG_BASE_HP_PER_TABLE);
        upsertPredefinedInterPodCard(
                "DOOR_BETWEEN_WORLDS",
                InterPodCardCategory.PLAN,
                DOOR_BETWEEN_WORLDS_IMAGE_URL,
                DOOR_BETWEEN_WORLDS_BASE_THREAT_PER_TABLE);
    }

    private void upsertPredefinedInterPodCard(String name,
                                              InterPodCardCategory category,
                                              String imageUrl,
                                              int defaultMetricMax) {
        InterPodCardDefinition definition = interPodCardDefinitionRepository
                .findByTypeAndNameIgnoreCase(InterPodCardDefinitionType.PREDEFINED, name)
                .orElseGet(InterPodCardDefinition::new);
        definition.setName(name);
        definition.setType(InterPodCardDefinitionType.PREDEFINED);
        definition.setCardCategory(category);
        definition.setDefaultMetricMax(defaultMetricMax);
        definition.setRuleType(VillainRuleType.MULTIPLIER);
        definition.setImagePath(imageUrl);
        definition.setImageContentType(DEFAULT_PREDEFINED_CARD_IMAGE_CONTENT_TYPE);
        definition.setOwner(null);
        interPodCardDefinitionRepository.save(definition);
    }

    private String resolveSeedOrganizerPassword() {
        if (seedOrganizerPassword != null && !seedOrganizerPassword.isBlank()) {
            return seedOrganizerPassword;
        }
        String generated = UUID.randomUUID().toString();
        log.warn("No se ha definido app.seed.organizer.password; se genera una contraseña temporal para el organizer: {}",
                generated);
        return generated;
    }
}
