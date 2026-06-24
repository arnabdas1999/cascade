package com.cascade.persistence;

import com.cascade.domain.model.RunStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of a run's state at a point in time.
 * This is what gets serialized to Postgres after every step —
 * the source of truth that makes crash recovery possible.
 */
public record RunSnapshot(
        String runId,
        String workflowId,
        RunStatus status,
        String currentStepId,
        Map<String, Object> contextOutputs, // all step outputs so far
        int attemptCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
