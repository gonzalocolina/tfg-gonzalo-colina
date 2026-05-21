package com.tfg.gonzalo.backend.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventLogActionTypeConstraintCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EventLogActionTypeConstraintCleanup.class);

    private final JdbcTemplate jdbcTemplate;

    public EventLogActionTypeConstraintCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> constraintNames = jdbcTemplate.queryForList("""
                    SELECT tc.CONSTRAINT_NAME
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                     AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                    WHERE tc.TABLE_NAME = 'EVENT_LOGS'
                      AND tc.CONSTRAINT_TYPE = 'CHECK'
                      AND UPPER(cc.CHECK_CLAUSE) LIKE '%ACTION_TYPE%'
                    """, String.class);

            for (String constraintName : constraintNames) {
                String safeConstraintName = constraintName.replace("\"", "\"\"");
                jdbcTemplate.execute("ALTER TABLE event_logs DROP CONSTRAINT IF EXISTS \"" + safeConstraintName + "\"");
                log.info("Constraint legacy eliminada de event_logs.action_type: {}", constraintName);
            }
        } catch (Exception exception) {
            log.warn("No se ha podido sanear las constraints legacy de event_logs.action_type: {}", exception.getMessage());
        }
    }
}
