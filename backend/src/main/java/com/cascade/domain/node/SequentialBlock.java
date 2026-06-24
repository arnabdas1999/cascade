package com.cascade.domain.node;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;

import java.util.List;

public final class SequentialBlock implements WorkflowNode {

    private final String id;
    private final List<WorkflowNode> children;

    public SequentialBlock(String id, List<WorkflowNode> children) {
        this.id = id;
        this.children = List.copyOf(children);
    }

    @Override
    public String id() { return id; }

    public List<WorkflowNode> children() { return children; }

    @Override
    public NodeResult accept(NodeVisitor visitor, ExecutionContext ctx) throws StepException, InterruptedException {
        return visitor.visitSequential(this, ctx);
    }
}
