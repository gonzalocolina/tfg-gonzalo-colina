package com.tfg.gonzalo.backend.exception;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import com.tfg.gonzalo.backend.support.DatabaseExceptionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    private static final String DATABASE_UNAVAILABLE_MESSAGE =
            "La base de datos no está disponible temporalmente. Espera unos segundos y reintenta. "
                    + "Si el problema continúa, reinicia el backend.";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenOperationException exception) {
        return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(RestExceptionHandler::formatFieldError)
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "La petición contiene datos no válidos.", details);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "El archivo enviado supera el tamaño máximo permitido.",
                List.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartError(MultipartException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "No se pudo procesar el formulario multipart enviado.",
                List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (DatabaseExceptionSupport.isMisclassifiedDatabaseFailure(exception)) {
            return handleDatabaseAccessFailure(exception);
        }

        String rawMessage = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        log.warn("Data integrity violation: {}", rawMessage, exception);
        String message = resolveDataIntegrityMessage(exception);
        return buildResponse(HttpStatus.BAD_REQUEST, message, List.of());
    }

    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<ApiErrorResponse> handleCannotGetJdbcConnection(
            CannotGetJdbcConnectionException exception) {
        return handleDatabaseAccessFailure(exception);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handlePessimisticLockingFailure(
            PessimisticLockingFailureException exception) {
        return handleDatabaseAccessFailure(exception);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiErrorResponse> handleCannotAcquireLock(CannotAcquireLockException exception) {
        return handleDatabaseAccessFailure(exception);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleQueryTimeout(QueryTimeoutException exception) {
        return handleDatabaseAccessFailure(exception);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccessResourceFailure(
            DataAccessResourceFailureException exception) {
        return handleDatabaseAccessFailure(exception);
    }

    private ResponseEntity<ApiErrorResponse> handleDatabaseAccessFailure(Throwable exception) {
        String rootCause = resolveRootCauseMessage(exception);
        log.warn("Database access failure: {}", rootCause, exception);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, DATABASE_UNAVAILABLE_MESSAGE, List.of());
    }

    private static String resolveRootCauseMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status,
                                                           String message,
                                                           List<String> details) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        );
        return ResponseEntity.status(status).body(response);
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String resolveDataIntegrityMessage(DataIntegrityViolationException exception) {
        String rawMessage = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        if (rawMessage == null) {
            return "Los datos enviados ya existen o entran en conflicto con otro registro.";
        }

        String normalized = rawMessage.toUpperCase(Locale.ROOT);
        if (normalized.contains("APP_USERS") && normalized.contains("EMAIL")) {
            return "Ya existe una cuenta con ese email.";
        }
        if (normalized.contains("APP_USERS") && normalized.contains("USERNAME")) {
            return "Ya existe un usuario con ese nombre de usuario.";
        }

        return "Los datos enviados ya existen o entran en conflicto con otro registro.";
    }
}
