package com.tfg.gonzalo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tfg.gonzalo.backend.support.DatabaseExceptionSupport;
import com.tfg.gonzalo.backend.support.DatabaseRetryExecutor;
import com.tfg.gonzalo.backend.support.DatabaseRetryLoggingListener;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class DatabaseRetryExecutorTest {

    @Test
    void retriesTransientDatabaseFailure() {
        RetryTemplate retryTemplate = buildRetryTemplate();
        DatabaseRetryExecutor executor = new DatabaseRetryExecutor(retryTemplate);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.getAndIncrement() == 0) {
                throw new CannotGetJdbcConnectionException("Connection lost", new SQLException("broken"));
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void retriesWhenDatabaseClosedIsWrappedInDataIntegrityViolation() {
        RetryTemplate retryTemplate = buildRetryTemplate();
        DatabaseRetryExecutor executor = new DatabaseRetryExecutor(retryTemplate);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.getAndIncrement() == 0) {
                throw new DataIntegrityViolationException(
                        "check constraint",
                        new SQLException("La base de datos ha sido cerrada [90098-240]"));
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void rethrowsAfterExhaustingRetries() {
        RetryTemplate retryTemplate = buildRetryTemplate();
        DatabaseRetryExecutor executor = new DatabaseRetryExecutor(retryTemplate);

        assertThrows(CannotGetJdbcConnectionException.class, () -> executor.execute(() -> {
            throw new CannotGetJdbcConnectionException("Connection lost", new SQLException("broken"));
        }));
    }

    @Test
    void databaseExceptionSupportDetectsClosedDatabaseMessages() {
        assertEquals(true, DatabaseExceptionSupport.isDatabaseClosedFailure(
                new DataIntegrityViolationException(
                        "check constraint",
                        new SQLException("The database has been closed [90098-240]"))));
    }

    private RetryTemplate buildRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(1)
                .delay(Duration.ZERO)
                .predicate(DatabaseExceptionSupport::isTransientDatabaseFailure)
                .build();

        RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
        retryTemplate.setRetryListener(new DatabaseRetryLoggingListener());
        return retryTemplate;
    }
}
