package com.cascade.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates the mutable state that flows through a run.
 * Steps read prior outputs and write their own — but never touch the raw map directly.
 */
public final class ExecutionContext {

    private final Map<String, Object> outputs = new LinkedHashMap<>();
    private final Map<String, Object> initialInputs;

    public ExecutionContext(Map<String, Object> initialInputs) {
        this.initialInputs = Map.copyOf(initialInputs);
        this.outputs.putAll(this.initialInputs);
    }

    public static ExecutionContext empty() {
        return new ExecutionContext(Map.of());
    }

    /**
     * Reconstruct a context from a persisted snapshot.
     * All prior step outputs are pre-loaded so the engine can skip already-completed steps.
     */
    public static ExecutionContext recover(Map<String, Object> persistedOutputs) {
        ExecutionContext ctx = new ExecutionContext(Map.of());
        persistedOutputs.forEach(ctx.outputs::put);
        return ctx;
    }

    /** Called by AbstractStep after a successful doExecute(). */
    public void record(String stepId, Object output) {
        outputs.put(stepId, output);
    }

    public Optional<Object> getOutput(String stepId) {
        return Optional.ofNullable(outputs.get(stepId));
    }

    public Map<String, Object> getAllOutputs() {
        return Collections.unmodifiableMap(outputs);
    }

    public Map<String, Object> getInitialInputs() {
        return initialInputs;
    }
}
