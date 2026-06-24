package com.cascade.api.dto;

import com.cascade.domain.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record WorkflowResponse(
        String id,
        String name,
        JsonNode definition,
        Instant createdAt
) {
    public static WorkflowResponse from(WorkflowDefinition def) {
        return new WorkflowResponse(def.id(), def.name(), def.definitionJson(), def.createdAt());
    }
}
