package com.nexuspulse.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record EventPayload(
    @NotBlank(message = "eventId is mandatory")
    String eventId,
    
    @NotBlank(message = "type is mandatory")
    String type,
    
    @NotNull(message = "payload cannot be null")
    Map<String, Object> payload,
    
    @NotBlank(message = "idempotencyKey is mandatory")
    String idempotencyKey
) {}