package com.cascade.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkflowRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "definition is required") JsonNode definition
) {}
