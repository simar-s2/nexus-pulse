package com.nexuspulse.backend.model;

public enum EventStatus {
    RECEIVED,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED;
    
    public boolean canTransitionTo(EventStatus next) {
        return switch (this) {
            case RECEIVED -> next == QUEUED;
            case QUEUED -> next == PROCESSING;
            case PROCESSING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}