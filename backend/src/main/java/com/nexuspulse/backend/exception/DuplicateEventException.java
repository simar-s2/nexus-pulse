package com.nexuspulse.backend.exception;

public class DuplicateEventException extends RuntimeException {
    private final String idempotencyKey;
    private final String traceId;
    
    public DuplicateEventException(String idempotencyKey, String traceId) {
        super("Event already processed: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
        this.traceId = traceId;
    }
    
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTraceId() { return traceId; }
}