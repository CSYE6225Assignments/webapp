package com.example.healthcheckapi.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Void> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Void> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Void> handleInvalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Void> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Void> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Handle authentication failures due to database being down
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<Void> handleAuthenticationServiceException(InternalAuthenticationServiceException ex) {
        // Check if it's a database connection issue
        Throwable cause = ex.getCause();
        if (cause instanceof DataAccessException ||
                (cause != null && cause.getMessage() != null &&
                        cause.getMessage().contains("Connection"))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    // Handle database connection issues
    @ExceptionHandler({
            CannotGetJdbcConnectionException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<Void> handleDatabaseConnectionError(Exception ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    // Handle general data access exceptions
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Void> handleDataAccessException(DataAccessException ex) {
        // If it's not a data integrity violation (which we handle above),
        // it's likely a connection issue
        if (!(ex instanceof DataIntegrityViolationException)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}