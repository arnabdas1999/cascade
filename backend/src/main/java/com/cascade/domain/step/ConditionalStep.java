package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.NodeVisitor;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.domain.retry.NoRetry;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Evaluates a SpEL condition on the execution context and routes to
 * trueBranch or falseBranch.  Overrides accept() directly so the visitor
 * is threaded through to the chosen branch — this is how a composite step
 * can contain sub-nodes without breaking the sealed WorkflowNode hierarchy.
 *
 * Example condition: "#step1['value'] > 10"
 */
public final class ConditionalStep extends AbstractStep {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final String conditionExpr;
    private final WorkflowNode trueBranch;
    private final WorkflowNode falseBranch; // may be null

    public ConditionalStep(String id, String conditionExpr,
                           WorkflowNode trueBranch, WorkflowNode falseBranch) {
        super(id, NoRetry.INSTANCE);
        this.conditionExpr = conditionExpr;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    /**
     * Override accept() so the engine (visitor) is forwarded to the chosen branch
     * rather than calling visitStep() on this node.
     */
    @Override
    public NodeResult accept(NodeVisitor visitor, ExecutionContext ctx)
            throws StepException, InterruptedException {
        boolean condition = evaluate(ctx);
        WorkflowNode branch = condition ? trueBranch : falseBranch;
        if (branch == null) return NodeResult.empty();
        return branch.accept(visitor, ctx);
    }

    @Override
    protected StepResult doExecute(ExecutionContext ctx) {
        // Never reached — accept() routes directly to a branch
        throw new UnsupportedOperationException("ConditionalStep routes via accept()");
    }

    private boolean evaluate(ExecutionContext ctx) throws StepException {
        try {
            var evalCtx = new StandardEvaluationContext();
            ctx.getAllOutputs().forEach((k, v) -> evalCtx.setVariable(k, v));
            Boolean result = PARSER.parseExpression(conditionExpr).getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            throw new StepException(id(), "Condition evaluation failed: " + e.getMessage(), e, false);
        }
    }

    public String conditionExpr() { return conditionExpr; }
    public WorkflowNode trueBranch() { return trueBranch; }
    public WorkflowNode falseBranch() { return falseBranch; }
}
