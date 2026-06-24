package com.cascade.api.dto;

import com.cascade.domain.model.RunStatus;
import com.cascade.persistence.RunSnapshot;

import java.time.Instant;
import java.util.Map;

public record RunResponse(
        String id,
        String workflowId,
        RunStatus status,
        String currentStepId,
        Map<String, Object> outputs,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static RunResponse from(RunSnapshot snapshot) {
        return new RunResponse(
                snapshot.runId(),
                snapshot.workflowId(),
                snapshot.status(),
                snapshot.currentStepId(),
                snapshot.contextOutputs(),
                snapshot.errorMessage(),
                snapshot.createdAt(),
                snapshot.updatedAt());
    }
}
