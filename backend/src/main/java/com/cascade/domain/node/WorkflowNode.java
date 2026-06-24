package com.cascade.domain.node;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;

/**
 * Composite pattern root. Every element in a workflow tree — a leaf step, a
 * sequential block, or a parallel block — is a WorkflowNode.
 *
 * Sealed so the compiler (and pattern-match switches) know the full set of
 * subtypes. Step is non-sealed so arbitrary step implementations are allowed.
 */
public sealed interface WorkflowNode permits Step, SequentialBlock, ParallelBlock {

    String id();

    NodeResult accept(NodeVisitor visitor, ExecutionContext ctx) throws StepException, InterruptedException;
}
