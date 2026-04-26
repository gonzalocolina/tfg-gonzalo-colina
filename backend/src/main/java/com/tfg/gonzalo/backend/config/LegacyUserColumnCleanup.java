package com.tfg.gonzalo.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LegacyUserColumnCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyUserColumnCleanup.class);

    private final JdbcTemplate jdbcTemplate;

    public LegacyUserColumnCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE app_users DROP COLUMN IF EXISTS display_name");
        } catch (Exception exception) {
            log.warn("No se ha podido eliminar la columna legacy app_users.display_name: {}", exception.getMessage());
        }
    }
}