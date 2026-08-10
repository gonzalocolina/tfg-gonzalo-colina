package com.tfg.gonzalo.backend.support;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatabaseHeartbeatSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DatabaseHeartbeatScheduler scheduler;

    @Test
    void pingDatabaseExecutesSelectOne() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        scheduler.pingDatabase();

        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }
}
