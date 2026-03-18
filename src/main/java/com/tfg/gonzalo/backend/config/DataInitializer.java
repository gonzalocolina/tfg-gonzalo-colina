package com.tfg.gonzalo.backend.config;

import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository appUserRepository,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void run(ApplicationArguments args) {
        try {
            if (appUserRepository.existsByUsername("admin")) {
                AppUser existing = appUserRepository.findByUsername("admin").get();
                existing.setPasswordHash(passwordEncoder.encode("admin"));
                appUserRepository.save(existing);
                return;
            }

            AppUser admin = new AppUser();
            admin.setUsername("admin");
            admin.setDisplayName("Administrador");
            admin.setPasswordHash(passwordEncoder.encode("admin"));
            admin.setRole(GlobalRole.ORGANIZER);
            admin.setEnabled(true);
            appUserRepository.save(admin);
        } catch (DataIntegrityViolationException e) {
            log.warn("DataInitializer: seed data skipped due to constraint violation ({})", e.getMessage());
        }
    }
}
