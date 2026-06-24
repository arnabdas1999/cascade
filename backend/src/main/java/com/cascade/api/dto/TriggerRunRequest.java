package com.cascade.api.dto;

import java.util.Map;

public record TriggerRunRequest(Map<String, Object> inputs) {
    public TriggerRunRequest {
        if (inputs == null) inputs = Map.of();
    }
}
