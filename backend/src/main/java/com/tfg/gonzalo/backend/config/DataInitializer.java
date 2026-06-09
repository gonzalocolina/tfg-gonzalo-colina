package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.InterPodCard;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardDefinitionRepository;
import com.tfg.gonzalo.backend.repository.InterPodCardRepository;
import com.tfg.gonzalo.backend.repository.VillainDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;
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
    private static final InterPodCardStatus DEFAULT_PREDEFINED_CARD_STATUS = InterPodCardStatus.REVELADA;
    private static final String DEFAULT_PREDEFINED_CARD_IMAGE_CONTENT_TYPE = "image/jpeg";
    private static final String MANGOG_IMAGE_URL = "https://marvelcdb.com/bundles/cards/55041.jpg";
    private static final String DOOR_BETWEEN_WORLDS_IMAGE_URL = "https://marvelcdb.com/bundles/cards/55046.jpg";

    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final VillainDefinitionRepository villainDefinitionRepository;
    private final InterPodCardDefinitionRepository interPodCardDefinitionRepository;
    private final InterPodCardRepository interPodCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedOrganizerPassword;

    public DataInitializer(AppUserRepository appUserRepository,
                           EventRepository eventRepository,
                           VillainDefinitionRepository villainDefinitionRepository,
                           InterPodCardDefinitionRepository interPodCardDefinitionRepository,
                           InterPodCardRepository interPodCardRepository,
                           @Value("${app.seed.organizer.password:}") String seedOrganizerPassword,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.villainDefinitionRepository = villainDefinitionRepository;
        this.interPodCardDefinitionRepository = interPodCardDefinitionRepository;
        this.interPodCardRepository = interPodCardRepository;
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
                organizer.setRole(GlobalRole.ORGANIZER);
                organizer.setEnabled(true);
                appUserRepository.save(organizer);
            }

            ensurePredefinedLokiVillain();
            migrateInterPodDefinitions();
            migrateInterPodCards();
            ensurePredefinedInterPodCards();
        } catch (DataIntegrityViolationException e) {
            log.warn("DataInitializer: seed data skipped due to constraint violation ({})", e.getMessage());
        }
    }

    private void migrateInterPodDefinitions() {
        List<InterPodCardDefinition> definitions = interPodCardDefinitionRepository.findAll();
        if (definitions.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (InterPodCardDefinition definition : definitions) {
            if (definition.getCardCategory() == InterPodCardCategory.PLAN) {
                int migratedThreat = Math.max(
                        safeValue(definition.getDefaultThreatMax()),
                        safeValue(definition.getDefaultHpMax())
                );
                if (!Integer.valueOf(0).equals(definition.getDefaultHpMax())) {
                    definition.setDefaultHpMax(0);
                    changed = true;
                }
                if (!Integer.valueOf(migratedThreat).equals(definition.getDefaultThreatMax())) {
                    definition.setDefaultThreatMax(migratedThreat);
                    changed = true;
                }
                continue;
            }

            if (!Integer.valueOf(0).equals(definition.getDefaultThreatMax())) {
                definition.setDefaultThreatMax(0);
                changed = true;
            }
            if (definition.getDefaultHpMax() == null) {
                definition.setDefaultHpMax(0);
                changed = true;
            }
        }

        if (changed) {
            interPodCardDefinitionRepository.saveAll(definitions);
        }
    }

    private void migrateInterPodCards() {
        List<InterPodCard> cards = interPodCardRepository.findAll();
        if (cards.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (InterPodCard card : cards) {
            changed |= migrateInterPodCard(card);
        }

        if (changed) {
            interPodCardRepository.saveAll(cards);
        }
    }

    private boolean migrateInterPodCard(InterPodCard card) {
        if (card.getCardCategory() == InterPodCardCategory.PLAN) {
            return migratePlanInterPodCard(card);
        }

        return migrateNonPlanInterPodCard(card);
    }

    private boolean migratePlanInterPodCard(InterPodCard card) {
        int migratedThreatMax = Math.max(safeValue(card.getThreatMax()), safeValue(card.getHpMax()));
        int migratedThreatCurrent = Math.max(safeValue(card.getThreatCurrent()), safeValue(card.getHpCurrent()));

        boolean changed = false;
        changed |= updateValue(card.getHpMax(), 0, card::setHpMax);
        changed |= updateValue(card.getHpCurrent(), 0, card::setHpCurrent);
        changed |= updateValue(card.getThreatMax(), migratedThreatMax, card::setThreatMax);
        changed |= updateValue(card.getThreatCurrent(), migratedThreatCurrent, card::setThreatCurrent);
        return changed;
    }

    private boolean migrateNonPlanInterPodCard(InterPodCard card) {
        boolean changed = false;
        changed |= updateValue(card.getThreatMax(), 0, card::setThreatMax);
        changed |= updateValue(card.getThreatCurrent(), 0, card::setThreatCurrent);
        changed |= initializeIfNull(card.getHpMax(), 0, card::setHpMax);
        changed |= initializeIfNull(card.getHpCurrent(), 0, card::setHpCurrent);
        return changed;
    }

    private boolean updateValue(Integer currentValue, int targetValue, IntConsumer setter) {
        if (Integer.valueOf(targetValue).equals(currentValue)) {
            return false;
        }

        setter.accept(targetValue);
        return true;
    }

    private boolean initializeIfNull(Integer currentValue, int defaultValue, IntConsumer setter) {
        if (currentValue != null) {
            return false;
        }

        setter.accept(defaultValue);
        return true;
    }

    private int safeValue(Integer value) {
        return value == null ? 0 : Math.max(0, value);
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
        canonicalizePredefinedInterPodCard(
                "THE_MANGOG",
                InterPodCardCategory.ESBIRRO,
                MANGOG_IMAGE_URL,
                MANGOG_BASE_HP_PER_TABLE,
                0);
        canonicalizePredefinedInterPodCard(
                "DOOR_BETWEEN_WORLDS",
                InterPodCardCategory.PLAN,
                DOOR_BETWEEN_WORLDS_IMAGE_URL,
                0,
                DOOR_BETWEEN_WORLDS_BASE_THREAT_PER_TABLE);
    }

    private void canonicalizePredefinedInterPodCard(String name,
                                                    InterPodCardCategory category,
                                                    String imageUrl,
                                                    int defaultHpMax,
                                                    int defaultThreatMax) {
        List<InterPodCardDefinition> definitions = interPodCardDefinitionRepository
                .findByTypeAndNameIgnoreCaseOrderByIdAsc(InterPodCardDefinitionType.PREDEFINED, name);

        InterPodCardDefinition canonical = definitions.isEmpty()
            ? createPredefinedInterPodCard(name, category, imageUrl, defaultHpMax, defaultThreatMax)
                : definitions.get(0);

        canonical.setName(name);
        canonical.setType(InterPodCardDefinitionType.PREDEFINED);
        canonical.setCardCategory(category);
        canonical.setDefaultHpMax(defaultHpMax);
        canonical.setDefaultThreatMax(defaultThreatMax);
        canonical.setDefaultStatus(DEFAULT_PREDEFINED_CARD_STATUS);
        canonical.setImagePath(imageUrl);
        canonical.setImageContentType(DEFAULT_PREDEFINED_CARD_IMAGE_CONTENT_TYPE);
        canonical.setOwner(null);
        canonical = interPodCardDefinitionRepository.save(canonical);

        if (definitions.size() <= 1) {
            return;
        }

        List<InterPodCardDefinition> duplicates = new ArrayList<>(definitions.subList(1, definitions.size()));
        reassignDuplicateDefinitionReferences(duplicates, canonical);
        interPodCardDefinitionRepository.deleteAll(duplicates);
        log.warn("Se han fusionado {} definiciones inter-pod predefinidas duplicadas para {}.",
                duplicates.size(), name);
    }

    private InterPodCardDefinition createPredefinedInterPodCard(String name,
                                                                InterPodCardCategory category,
                                                                String imageUrl,
                                                                int defaultHpMax,
                                                                int defaultThreatMax) {
        InterPodCardDefinition definition = new InterPodCardDefinition();
        definition.setName(name);
        definition.setType(InterPodCardDefinitionType.PREDEFINED);
        definition.setCardCategory(category);
        definition.setDefaultHpMax(defaultHpMax);
        definition.setDefaultThreatMax(defaultThreatMax);
        definition.setDefaultStatus(DEFAULT_PREDEFINED_CARD_STATUS);
        definition.setImagePath(imageUrl);
        definition.setImageContentType(DEFAULT_PREDEFINED_CARD_IMAGE_CONTENT_TYPE);
        definition.setOwner(null);
        return interPodCardDefinitionRepository.save(definition);
    }

    private void reassignDuplicateDefinitionReferences(List<InterPodCardDefinition> duplicates,
                                                       InterPodCardDefinition canonical) {
        for (InterPodCardDefinition duplicate : duplicates) {
            reassignInterPodCardInstances(duplicate, canonical);
        }
    }

    private void reassignInterPodCardInstances(InterPodCardDefinition duplicate,
                                               InterPodCardDefinition canonical) {
        var cards = interPodCardRepository.findByInterPodCardDefinitionId(duplicate.getId());
        if (cards.isEmpty()) {
            return;
        }

        for (var card : cards) {
            card.setInterPodCardDefinition(canonical);
        }
        interPodCardRepository.saveAll(cards);
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
