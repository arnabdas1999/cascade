package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.retry.RetryPolicy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Evaluates a SpEL expression against the current execution context outputs.
 *
 * Example expression: "#fetch['price'] * 2"
 * where "fetch" is a prior step's id whose output is in context.
 */
public final class TransformStep extends AbstractStep {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final String expression;

    public TransformStep(String id, String expression, RetryPolicy retryPolicy) {
        super(id, retryPolicy);
        this.expression = expression;
    }

    public TransformStep(String id, String expression) {
        super(id);
        this.expression = expression;
    }

    @Override
    protected void validate(ExecutionContext ctx) throws StepException {
        if (expression == null || expression.isBlank()) {
            throw new StepException(id(), "TransformStep requires a non-blank expression", false);
        }
    }

    @Override
    protected StepResult doExecute(ExecutionContext ctx) throws StepException {
        try {
            EvaluationContext evalCtx = new StandardEvaluationContext();
            // expose each prior step's output as a named SpEL variable: #stepId
            ctx.getAllOutputs().forEach((k, v) -> ((StandardEvaluationContext) evalCtx).setVariable(k, v));

            Expression expr = PARSER.parseExpression(expression);
            Object result = expr.getValue(evalCtx);
            return StepResult.success(result);
        } catch (Exception e) {
            throw new StepException(id(), "Expression evaluation failed: " + e.getMessage(), e, false);
        }
    }

    public String expression() { return expression; }
}
