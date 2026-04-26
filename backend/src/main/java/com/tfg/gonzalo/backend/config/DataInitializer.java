package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.Event;
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
    private static final int DEFAULT_PREDEFINED_CARD_HP = 8;
    private static final InterPodCardStatus DEFAULT_PREDEFINED_CARD_STATUS = InterPodCardStatus.REVELADA;

    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final VillainDefinitionRepository villainDefinitionRepository;
    private final InterPodCardDefinitionRepository interPodCardDefinitionRepository;
    private final InterPodCardRepository interPodCardRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedAdminPassword;

    public DataInitializer(AppUserRepository appUserRepository,
                           EventRepository eventRepository,
                           VillainDefinitionRepository villainDefinitionRepository,
                           InterPodCardDefinitionRepository interPodCardDefinitionRepository,
                           InterPodCardRepository interPodCardRepository,
                           @Value("${app.seed.admin.password:}") String seedAdminPassword,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.villainDefinitionRepository = villainDefinitionRepository;
        this.interPodCardDefinitionRepository = interPodCardDefinitionRepository;
        this.interPodCardRepository = interPodCardRepository;
        this.seedAdminPassword = seedAdminPassword;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void run(ApplicationArguments args) {
        try {
            String resolvedAdminPassword = resolveSeedAdminPassword();
            if (appUserRepository.existsByUsernameIgnoreCase(DEFAULT_ADMIN_USERNAME)) {
                AppUser existing = appUserRepository.findByUsernameIgnoreCase(DEFAULT_ADMIN_USERNAME)
                        .orElseThrow(() -> new IllegalStateException("Usuario administrador no encontrado."));
                existing.setPasswordHash(passwordEncoder.encode(resolvedAdminPassword));
                appUserRepository.save(existing);
            } else {
                AppUser admin = new AppUser();
                admin.setUsername(DEFAULT_ADMIN_USERNAME);
                admin.setPasswordHash(passwordEncoder.encode(resolvedAdminPassword));
                admin.setRole(GlobalRole.ORGANIZER);
                admin.setEnabled(true);
                appUserRepository.save(admin);
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
        canonicalizePredefinedInterPodCard("THE_MANGOG", InterPodCardCategory.ESBIRRO);
        canonicalizePredefinedInterPodCard("DOOR_BETWEEN_WORLDS", InterPodCardCategory.PLAN);
    }

    private void canonicalizePredefinedInterPodCard(String name, InterPodCardCategory category) {
        List<InterPodCardDefinition> definitions = interPodCardDefinitionRepository
                .findByTypeAndNameIgnoreCaseOrderByIdAsc(InterPodCardDefinitionType.PREDEFINED, name);

        InterPodCardDefinition canonical = definitions.isEmpty()
                ? createPredefinedInterPodCard(name, category)
                : definitions.get(0);

        canonical.setName(name);
        canonical.setType(InterPodCardDefinitionType.PREDEFINED);
        canonical.setCardCategory(category);
        canonical.setDefaultHpMax(DEFAULT_PREDEFINED_CARD_HP);
        canonical.setDefaultStatus(DEFAULT_PREDEFINED_CARD_STATUS);
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

    private InterPodCardDefinition createPredefinedInterPodCard(String name, InterPodCardCategory category) {
        InterPodCardDefinition definition = new InterPodCardDefinition();
        definition.setName(name);
        definition.setType(InterPodCardDefinitionType.PREDEFINED);
        definition.setCardCategory(category);
        definition.setDefaultHpMax(DEFAULT_PREDEFINED_CARD_HP);
        definition.setDefaultStatus(DEFAULT_PREDEFINED_CARD_STATUS);
        definition.setOwner(null);
        return interPodCardDefinitionRepository.save(definition);
    }

    private void reassignDuplicateDefinitionReferences(List<InterPodCardDefinition> duplicates,
                                                       InterPodCardDefinition canonical) {
        for (InterPodCardDefinition duplicate : duplicates) {
            reassignInterPodCardInstances(duplicate, canonical);
            reassignEventSelections(duplicate, canonical);
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

    private void reassignEventSelections(InterPodCardDefinition duplicate,
                                         InterPodCardDefinition canonical) {
        List<Event> updatedEvents = new ArrayList<>();

        for (Event event : eventRepository.findAll()) {
            boolean removed = event.getInterPodCardDefinitions().removeIf(definition -> duplicate.getId().equals(definition.getId()));
            if (!removed) {
                continue;
            }

            event.getInterPodCardDefinitions().add(canonical);
            updatedEvents.add(event);
        }

        if (!updatedEvents.isEmpty()) {
            eventRepository.saveAll(updatedEvents);
        }
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
