package com.tfg.gonzalo.backend.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;

class EventLogActionTypeConstraintCleanupTest {

    @Test
    void dropsLegacyActionTypeCheckConstraints() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("CONSTRAINT_ABC", "CHK_EVENT_LOGS_ACTION_TYPE"));

        EventLogActionTypeConstraintCleanup cleanup = new EventLogActionTypeConstraintCleanup(jdbcTemplate);

        cleanup.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate).execute("ALTER TABLE event_logs DROP CONSTRAINT IF EXISTS \"CONSTRAINT_ABC\"");
        verify(jdbcTemplate).execute("ALTER TABLE event_logs DROP CONSTRAINT IF EXISTS \"CHK_EVENT_LOGS_ACTION_TYPE\"");
    }

    @Test
    void doesNothingWhenThereAreNoLegacyConstraints() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        EventLogActionTypeConstraintCleanup cleanup = new EventLogActionTypeConstraintCleanup(jdbcTemplate);

        cleanup.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
