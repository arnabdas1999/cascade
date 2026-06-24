package com.cascade.domain.node;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;

public interface NodeVisitor {

    NodeResult visitStep(Step step, ExecutionContext ctx) throws StepException, InterruptedException;

    NodeResult visitSequential(SequentialBlock block, ExecutionContext ctx) throws StepException, InterruptedException;

    NodeResult visitParallel(ParallelBlock block, ExecutionContext ctx) throws StepException, InterruptedException;
}
