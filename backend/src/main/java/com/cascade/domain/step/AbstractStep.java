package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.NodeVisitor;
import com.cascade.domain.node.Step;
import com.cascade.domain.retry.NoRetry;
import com.cascade.domain.retry.RetryPolicy;

/**
 * Template Method: defines the fixed skeleton (validate → execute → record).
 * Subclasses override doExecute() and optionally validate().
 */
public abstract class AbstractStep implements Step {

    private final String id;
    private final RetryPolicy retryPolicy;

    protected AbstractStep(String id, RetryPolicy retryPolicy) {
        this.id = id;
        this.retryPolicy = retryPolicy;
    }

    protected AbstractStep(String id) {
        this(id, NoRetry.INSTANCE);
    }

    @Override
    public String id() { return id; }

    @Override
    public RetryPolicy retryPolicy() { return retryPolicy; }

    /** Template method — final so the skeleton is never overridden. */
    @Override
    public final StepResult execute(ExecutionContext ctx) throws StepException {
        validate(ctx);
        StepResult result = doExecute(ctx);
        if (result.success() && result.output() != null) {
            ctx.record(id, result.output());
        }
        return result;
    }

    /** Optional pre-execution validation hook. */
    protected void validate(ExecutionContext ctx) throws StepException {}

    /** The step-specific work. Throw StepException on failure. */
    protected abstract StepResult doExecute(ExecutionContext ctx) throws StepException;

    @Override
    public NodeResult accept(NodeVisitor visitor, ExecutionContext ctx) throws StepException, InterruptedException {
        return visitor.visitStep(this, ctx);
    }
}
