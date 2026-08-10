package com.tfg.gonzalo.backend.support;

import java.util.Locale;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

public final class DatabaseExceptionSupport {

    private DatabaseExceptionSupport() {
    }

    public static boolean isTransientDatabaseFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof CannotGetJdbcConnectionException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof CannotAcquireLockException
                    || current instanceof QueryTimeoutException) {
                return true;
            }
            if (isDatabaseClosedMessage(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDatabaseClosedFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (isDatabaseClosedMessage(current.getMessage())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMisclassifiedDatabaseFailure(DataIntegrityViolationException exception) {
        return isDatabaseClosedFailure(exception);
    }

    private static boolean isDatabaseClosedMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("90098")
                || normalized.contains("database has been closed")
                || normalized.contains("base de datos ha sido cerrada");
    }
}
