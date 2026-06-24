package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.NodeVisitor;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.domain.retry.RetryPolicy;

import java.util.Map;

/**
 * A step that delegates to an embedded sub-workflow tree.
 * The sub-tree is parsed and inlined at definition time by StepFactory,
 * so no runtime DB lookup is needed — the engine just walks the embedded tree.
 *
 * Example JSON:
 * {
 *   "type": "subworkflow",
 *   "id": "run-sub",
 *   "definition": { "root": { "type": "sequential", "children": [...] } }
 * }
 */
public final class SubWorkflowStep extends AbstractStep {

    private final WorkflowNode subTree;

    public SubWorkflowStep(String id, WorkflowNode subTree, RetryPolicy retryPolicy) {
        super(id, retryPolicy);
        this.subTree = subTree;
    }

    /** Override accept() to forward the visitor into the sub-tree. */
    @Override
    public NodeResult accept(NodeVisitor visitor, ExecutionContext ctx)
            throws StepException, InterruptedException {
        NodeResult subResult = subTree.accept(visitor, ctx);
        // Wrap sub-tree outputs under this step's id so callers can reference them
        if (subResult.success()) {
            ctx.record(id(), subResult.outputs());
        }
        return subResult;
    }

    @Override
    protected StepResult doExecute(ExecutionContext ctx) {
        throw new UnsupportedOperationException("SubWorkflowStep routes via accept()");
    }

    public WorkflowNode subTree() { return subTree; }
}
