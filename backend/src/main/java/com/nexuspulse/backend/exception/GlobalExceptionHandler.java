package com.nexuspulse.backend.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<Object> handleDuplicate(DuplicateEventException ex) {
        // AGENTS.md 1.2: Idempotent behavior - Return 200 OK
        return ResponseEntity.ok(Map.of(
            "status", "DUPLICATE",
            "message", ex.getMessage(),
            "traceId", ex.getTraceId()
        ));
    }

    @ExceptionHandler(DownstreamUnavailableException.class)
    public ResponseEntity<Object> handleDownstream(DownstreamUnavailableException ex) {
        // AGENTS.md 1: Fail loudly
        logger.error("Downstream failure. eventId={}", ex.getEventId(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "error", "Service Unavailable",
            "message", "Ingestion temporarily failed, please retry",
            "traceId", ex.getTraceId(),
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Validation Failed",
            "details", errors
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex) {
        logger.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", "Internal Server Error",
            "traceId", MDC.get("traceId") != null ? MDC.get("traceId") : "N/A",
            "timestamp", Instant.now().toString()
        ));
    }
}