package com.cascade.domain.step;

import com.cascade.domain.node.NodeResult;

import java.util.Map;

public record StepResult(boolean success, Object output, String errorMessage) {

    public static StepResult success(Object output) {
        return new StepResult(true, output, null);
    }

    public static StepResult failure(String errorMessage) {
        return new StepResult(false, null, errorMessage);
    }

    public NodeResult toNodeResult(String stepId) {
        if (success) {
            return output != null
                    ? NodeResult.success(Map.of(stepId, output))
                    : NodeResult.empty();
        }
        return NodeResult.failure(errorMessage);
    }
}
