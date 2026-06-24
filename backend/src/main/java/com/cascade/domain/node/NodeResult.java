package com.cascade.domain.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record NodeResult(boolean success, Map<String, Object> outputs, String errorMessage) {

    public static NodeResult success(Map<String, Object> outputs) {
        return new NodeResult(true, Collections.unmodifiableMap(new LinkedHashMap<>(outputs)), null);
    }

    public static NodeResult success(String key, Object value) {
        return success(Map.of(key, value));
    }

    public static NodeResult empty() {
        return success(Map.of());
    }

    public static NodeResult failure(String errorMessage) {
        return new NodeResult(false, Map.of(), errorMessage);
    }

    /** Merge a list of results, short-circuiting on the first failure. */
    public static NodeResult merge(List<NodeResult> results) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (NodeResult r : results) {
            if (!r.success()) return r;
            merged.putAll(r.outputs());
        }
        return success(merged);
    }
}
