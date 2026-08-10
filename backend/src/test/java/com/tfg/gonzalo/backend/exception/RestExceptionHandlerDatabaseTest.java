package com.tfg.gonzalo.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

class RestExceptionHandlerDatabaseTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void handleCannotGetJdbcConnectionReturnsServiceUnavailable() {
        ResponseEntity<ApiErrorResponse> response = handler.handleCannotGetJdbcConnection(
                new CannotGetJdbcConnectionException("Connection refused", new SQLException("db down")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().message().contains("temporalmente"));
        assertTrue(response.getBody().message().contains("reinicia el backend"));
    }

    @Test
    void handleDataIntegrityViolationWithClosedDatabaseReturnsServiceUnavailable() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException(
                        "check constraint invalid",
                        new SQLException("La base de datos ha sido cerrada [90098-240]")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().message().contains("temporalmente"));
    }

    @Test
    void handleDataIntegrityViolationStillReturnsBadRequestForRealDuplicates() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key", new SQLException("unique constraint")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                "Los datos enviados ya existen o entran en conflicto con otro registro.",
                response.getBody().message());
    }
}
