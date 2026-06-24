package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.NodeVisitor;
import com.cascade.domain.node.Step;
import com.cascade.domain.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator: wraps any Step with timing + outcome logging.
 * Adds behaviour without modifying the wrapped step — OCP in action.
 *
 * Can be stacked: new LoggingStepDecorator(new TimingStepDecorator(realStep))
 */
public final class LoggingStepDecorator implements Step {

    private static final Logger log = LoggerFactory.getLogger(LoggingStepDecorator.class);

    private final Step delegate;

    public LoggingStepDecorator(Step delegate) {
        this.delegate = delegate;
    }

    @Override
    public StepResult execute(ExecutionContext ctx) throws StepException {
        log.info("[step:{}] starting", id());
        long start = System.currentTimeMillis();
        try {
            StepResult result = delegate.execute(ctx);
            log.info("[step:{}] completed in {}ms, success={}", id(),
                    System.currentTimeMillis() - start, result.success());
            return result;
        } catch (StepException e) {
            log.warn("[step:{}] failed in {}ms: {}", id(),
                    System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    @Override
    public void compensate(ExecutionContext ctx) {
        log.info("[step:{}] compensating", id());
        delegate.compensate(ctx);
    }

    @Override
    public String id() { return delegate.id(); }

    @Override
    public RetryPolicy retryPolicy() { return delegate.retryPolicy(); }

    @Override
    public NodeResult accept(NodeVisitor visitor, ExecutionContext ctx)
            throws StepException, InterruptedException {
        return visitor.visitStep(this, ctx);
    }
}
