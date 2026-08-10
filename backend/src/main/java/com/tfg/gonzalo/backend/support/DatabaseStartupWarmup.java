package com.tfg.gonzalo.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStartupWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupWarmup.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseStartupWarmup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        log.info("Database warmup completed on startup.");
    }
}
