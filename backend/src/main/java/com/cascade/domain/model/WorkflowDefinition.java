package com.cascade.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of a workflow definition as posted by the user.
 * The raw JSON is preserved for re-parsing on every run and for persistence.
 */
public record WorkflowDefinition(
        String id,
        String name,
        JsonNode definitionJson,
        Instant createdAt
) {
    public static WorkflowDefinition create(String name, JsonNode definitionJson) {
        return new WorkflowDefinition(UUID.randomUUID().toString(), name, definitionJson, Instant.now());
    }
}
