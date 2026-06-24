package com.cascade.domain.event;

import java.time.Instant;
import java.util.UUID;

public record RunEvent(
        String id,
        String runId,
        RunEventType type,
        String stepId,
        Instant timestamp,
        String detail
) {
    public static RunEvent of(String runId, RunEventType type, String stepId, String detail) {
        return new RunEvent(UUID.randomUUID().toString(), runId, type, stepId, Instant.now(), detail);
    }
}
