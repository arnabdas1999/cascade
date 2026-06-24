package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.retry.RetryPolicy;

import java.util.Map;

/** Pauses execution for a configurable number of milliseconds. */
public final class DelayStep extends AbstractStep {

    private final long delayMs;

    public DelayStep(String id, long delayMs, RetryPolicy retryPolicy) {
        super(id, retryPolicy);
        this.delayMs = delayMs;
    }

    public DelayStep(String id, long delayMs) {
        super(id);
        this.delayMs = delayMs;
    }

    @Override
    protected StepResult doExecute(ExecutionContext ctx) throws StepException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepException(id(), "Delay interrupted", e, false);
        }
        return StepResult.success(Map.of("delayMs", delayMs, "status", "completed"));
    }

    public long delayMs() { return delayMs; }
}
