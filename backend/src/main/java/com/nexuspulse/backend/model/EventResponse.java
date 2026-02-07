package com.nexuspulse.backend.model;

import java.time.Instant;

public record EventResponse(
    String eventId,
    String status,
    String message,
    String traceId,
    String timestamp
) {
    public static EventResponse accepted(String eventId, String traceId) {
        return new EventResponse(
            eventId, 
            "ACCEPTED", 
            "Event queued for processing", 
            traceId, 
            Instant.now().toString()
        );
    }

    public static EventResponse duplicate(String eventId, String traceId) {
        return new EventResponse(
            eventId, 
            "DUPLICATE", 
            "Event already processed", 
            traceId, 
            Instant.now().toString()
        );
    }
}