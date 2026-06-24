package com.cascade.engine;

import com.cascade.domain.event.RunEventListener;
import com.cascade.domain.event.RunEventType;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.node.Step;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.persistence.RunSnapshot;
import com.cascade.persistence.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saga orchestration: when a run fails, walk the already-completed steps
 * in reverse order and call compensate() on each.
 *
 * Compensation is itself durable: progress is snapshotted after each compensated
 * step so a crash mid-compensation doesn't leave things in an unknown state.
 */
@Component
public class CompensationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CompensationOrchestrator.class);

    private final List<RunEventListener> listeners;

    public CompensationOrchestrator(List<RunEventListener> listeners) {
        this.listeners = listeners;
    }

    /**
     * Collect all leaf Step nodes in the order they appear in the tree,
     * then run compensate() on completed ones in reverse.
     *
     * @param root   the workflow's root node
     * @param run    the failed run (must be in FAILED status before calling)
     * @param ctx    the execution context at the point of failure
     * @param store  nullable; if present, snapshot is saved after each compensation
     */
    public void compensate(WorkflowNode root, Run run, ExecutionContext ctx,
                           @Nullable StateStore store) {
        run.startCompensation();
        publish(run.id(), null, RunEventType.RUN_COMPENSATING, null);

        List<Step> orderedSteps = collectLeafSteps(root);
        Collections.reverse(orderedSteps); // undo in reverse order

        for (Step step : orderedSteps) {
            // Only compensate steps that actually ran (have output in context)
            if (ctx.getOutput(step.id()).isEmpty()) continue;

            publish(run.id(), step.id(), RunEventType.STEP_COMPENSATING, null);
            log.info("Compensating step {} for run {}", step.id(), run.id());
            try {
                step.compensate(ctx);
                publish(run.id(), step.id(), RunEventType.STEP_COMPENSATED, null);
                log.info("Compensated step {}", step.id());
            } catch (Exception e) {
                log.warn("Compensation of step {} failed: {}", step.id(), e.getMessage());
                publish(run.id(), step.id(), RunEventType.STEP_COMPENSATING,
                        "compensation threw: " + e.getMessage());
            }

            if (store != null) {
                try {
                    store.saveSnapshot(new RunSnapshot(
                            run.id(), run.workflowId(), run.status(),
                            step.id(), ctx.getAllOutputs(),
                            run.attemptCount(), run.errorMessage(),
                            run.createdAt(), Instant.now()));
                } catch (Exception e) {
                    log.warn("Failed to snapshot compensation progress: {}", e.getMessage());
                }
            }
        }

        run.compensated();
        publish(run.id(), null, RunEventType.RUN_COMPENSATED, null);
        log.info("Run {} fully compensated", run.id());
    }

    /** Flatten a WorkflowNode tree to its leaf Steps in declaration order. */
    private List<Step> collectLeafSteps(WorkflowNode node) {
        List<Step> steps = new ArrayList<>();
        collectRecursive(node, steps);
        return steps;
    }

    private void collectRecursive(WorkflowNode node, List<Step> acc) {
        switch (node) {
            case SequentialBlock seq -> seq.children().forEach(c -> collectRecursive(c, acc));
            case com.cascade.domain.node.ParallelBlock par ->
                    par.children().forEach(c -> collectRecursive(c, acc));
            case Step s -> acc.add(s);
        }
    }

    private void publish(String runId, String stepId, RunEventType type, String detail) {
        for (RunEventListener l : listeners) {
            try {
                l.onEvent(com.cascade.domain.event.RunEvent.of(runId, type, stepId, detail));
            } catch (Exception e) {
                log.warn("Listener threw on {}: {}", type, e.getMessage());
            }
        }
    }
}
