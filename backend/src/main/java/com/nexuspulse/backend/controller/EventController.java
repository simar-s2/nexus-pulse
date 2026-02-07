package com.nexuspulse.backend.controller;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexuspulse.backend.model.EventPayload;
import com.nexuspulse.backend.model.EventResponse;
import com.nexuspulse.backend.service.EventPublisherService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventPublisherService publisherService;

    public EventController(EventPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventPayload payload) {
        // AGENTS.md 6.2: TraceId generation at boundary
        String traceId = UUID.randomUUID().toString();
        
        MDC.put("traceId", traceId);
        MDC.put("eventId", payload.eventId());

        try {
            publisherService.publishEvent(payload, traceId);
            
            // AGENTS.md 1: Response < 50ms (Accepted)
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(EventResponse.accepted(payload.eventId(), traceId));
        } finally {
            MDC.clear();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}