package com.tfg.gonzalo.backend.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene viva la conexión H2 embebida durante periodos largos sin tráfico (p. ej. demo/TFG).
 */
@Component
@ConditionalOnProperty(name = "app.database.heartbeat.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHeartbeatScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHeartbeatScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${app.database.heartbeat-interval-ms:60000}")
    public void pingDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result == null || result != 1) {
                log.warn("Database heartbeat returned unexpected value: {}", result);
            }
        } catch (RuntimeException exception) {
            log.error("Database heartbeat failed: {}", exception.getMessage());
        }
    }
}
