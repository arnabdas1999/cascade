package com.cascade.domain.node;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.retry.RetryPolicy;
import com.cascade.domain.step.StepResult;

/**
 * A leaf in the workflow tree: something executable.
 * Command pattern: execute() does the work, compensate() undoes it.
 * Non-sealed so any step implementation is allowed.
 *
 * Lives in domain.node so that the sealed WorkflowNode can permit it
 * (Java sealed types require permitted subtypes in the same package when
 * not using named modules).
 */
public non-sealed interface Step extends WorkflowNode {

    StepResult execute(ExecutionContext ctx) throws StepException;

    /** Saga compensation: undo this step's work. No-op by default. */
    default void compensate(ExecutionContext ctx) {}

    RetryPolicy retryPolicy();
}
