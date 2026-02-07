package com.nexuspulse.backend.exception;

public class DownstreamUnavailableException extends RuntimeException {
    private final String eventId;
    private final String traceId;
    
    public DownstreamUnavailableException(String eventId, String traceId, String message, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
        this.traceId = traceId;
    }
    
    public String getEventId() { return eventId; }
    public String getTraceId() { return traceId; }
}