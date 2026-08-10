package com.tfg.gonzalo.backend.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class DatabaseExceptionSupportTest {

    @Test
    void detectsClosedDatabaseInCauseChain() {
        assertTrue(DatabaseExceptionSupport.isDatabaseClosedFailure(
                new DataIntegrityViolationException(
                        "check constraint",
                        new SQLException("The database has been closed [90098-240]"))));
    }

    @Test
    void treatsClosedDatabaseAsTransient() {
        assertTrue(DatabaseExceptionSupport.isTransientDatabaseFailure(
                new DataIntegrityViolationException(
                        "check constraint",
                        new SQLException("La base de datos ha sido cerrada [90098-240]"))));
    }

    @Test
    void doesNotTreatDuplicateKeyAsClosedDatabase() {
        assertFalse(DatabaseExceptionSupport.isDatabaseClosedFailure(
                new DataIntegrityViolationException("duplicate key", new SQLException("unique constraint"))));
    }

    @Test
    void treatsConnectionFailuresAsTransient() {
        assertTrue(DatabaseExceptionSupport.isTransientDatabaseFailure(
                new CannotGetJdbcConnectionException("down", new SQLException("broken"))));
    }
}
