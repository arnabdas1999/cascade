package com.cascade.engine;

import com.cascade.common.StepException;
import com.cascade.domain.event.RunEvent;
import com.cascade.domain.event.RunEventListener;
import com.cascade.domain.event.RunEventType;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.NodeVisitor;
import com.cascade.domain.node.ParallelBlock;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.node.Step;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.domain.step.StepResult;
import com.cascade.persistence.RunSnapshot;
import com.cascade.persistence.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visitor that walks the WorkflowNode tree and executes each node.
 *
 * Durability: after every successful step a snapshot is persisted via StateStore.
 * Recovery: visitSequential skips steps whose outputs already exist in context.
 * Parallelism: visitParallel fans out onto virtual threads (Phase 3).
 *
 * ThreadLocal carries the per-run Run + StateStore so visitor methods don't
 * need extra parameters — safe because each concurrent run owns its virtual thread.
 */
@Component
public class ExecutionEngine implements NodeVisitor {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final List<RunEventListener> listeners;
    private final ExecutorService executor;

    /** Per-execution context — propagated explicitly into forked parallel threads. */
    private final ThreadLocal<ExecSession> session = new ThreadLocal<>();

    private record ExecSession(Run run, @Nullable StateStore stateStore) {}

    /** Full Spring-managed constructor. */
    @org.springframework.beans.factory.annotation.Autowired
    public ExecutionEngine(List<RunEventListener> listeners,
                           @Qualifier("workflowExecutor") ExecutorService executor) {
        this.listeners = listeners;
        this.executor = executor;
    }

    /** Convenience constructor for unit tests (no Spring context needed). */
    ExecutionEngine(List<RunEventListener> listeners) {
        this(listeners, Executors.newVirtualThreadPerTaskExecutor());
    }

    // ---- Entry points ----

    /** Full entry point with durability. */
    public NodeResult execute(WorkflowNode root, Run run, ExecutionContext ctx,
                              @Nullable StateStore stateStore) {
        session.set(new ExecSession(run, stateStore));
        try {
            return root.accept(this, ctx);
        } catch (StepException e) {
            log.error("Unhandled StepException in run {}: {}", run.id(), e.getMessage());
            return NodeResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeResult.failure("Execution interrupted");
        } finally {
            session.remove();
        }
    }

    /** Convenience for unit tests — no StateStore. */
    public NodeResult execute(WorkflowNode root, Run run, ExecutionContext ctx) {
        return execute(root, run, ctx, null);
    }

    // ---- NodeVisitor ----

    @Override
    public NodeResult visitSequential(SequentialBlock block, ExecutionContext ctx)
            throws StepException, InterruptedException {
        Map<String, Object> allOutputs = new LinkedHashMap<>();
        for (WorkflowNode child : block.children()) {
            // Idempotent replay: skip steps already completed before a crash
            if (child instanceof Step s && ctx.getOutput(s.id()).isPresent()) {
                log.debug("Skipping recovered step {}", s.id());
                ctx.getOutput(s.id()).ifPresent(v -> allOutputs.put(s.id(), v));
                continue;
            }
            NodeResult result = child.accept(this, ctx);
            if (!result.success()) return result;
            allOutputs.putAll(result.outputs());
        }
        return NodeResult.success(allOutputs);
    }

    /**
     * Fan-out: each child branch runs on its own virtual thread.
     * The ExecSession is propagated explicitly so ThreadLocal data is available
     * in each forked thread.
     * All branches must complete before the parallel block returns (join).
     */
    @Override
    public NodeResult visitParallel(ParallelBlock block, ExecutionContext ctx)
            throws StepException, InterruptedException {

        ExecSession current = session.get();

        List<CompletableFuture<NodeResult>> futures = block.children().stream()
                .map(child -> CompletableFuture.supplyAsync(() -> {
                    session.set(current); // propagate session into child thread
                    try {
                        return child.accept(this, ctx);
                    } catch (StepException | InterruptedException e) {
                        return NodeResult.failure(e.getMessage());
                    } finally {
                        session.remove();
                    }
                }, executor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            return NodeResult.failure("Parallel branch threw: " + e.getMessage());
        }

        List<NodeResult> results = new ArrayList<>();
        for (var f : futures) results.add(f.join());
        return NodeResult.merge(results);
    }

    @Override
    public NodeResult visitStep(Step step, ExecutionContext ctx)
            throws StepException, InterruptedException {

        ExecSession s = session.get();
        Run run = s != null ? s.run() : null;

        if (run != null) run.stepStarted(step.id());
        publish(run, step.id(), RunEventType.STEP_STARTED, "attempt 1");

        int attempt = 0;
        while (true) {
            try {
                StepResult sr = step.execute(ctx);
                if (run != null) run.stepCompleted(step.id());
                publish(run, step.id(), RunEventType.STEP_COMPLETED, null);
                persistSnapshot(run, ctx, s);
                return sr.toNodeResult(step.id());

            } catch (StepException e) {
                if (run != null) run.stepFailed(step.id(), e.getMessage());
                publish(run, step.id(), RunEventType.STEP_FAILED, e.getMessage());

                boolean canRetry = e.isRetryable() && step.retryPolicy().shouldRetry(attempt, e);
                if (!canRetry) {
                    return NodeResult.failure("[%s] %s".formatted(step.id(), e.getMessage()));
                }

                long waitMs = step.retryPolicy().backoff(attempt).toMillis();
                if (run != null) run.stepRetrying(step.id(), attempt + 2);
                publish(run, step.id(), RunEventType.STEP_RETRYING,
                        "waiting %dms, next attempt %d".formatted(waitMs, attempt + 2));
                log.debug("Step {} retrying in {}ms (attempt {})", step.id(), waitMs, attempt + 2);
                Thread.sleep(waitMs);
                attempt++;
            }
        }
    }

    // ---- Helpers ----

    private void persistSnapshot(Run run, ExecutionContext ctx, ExecSession s) {
        if (s == null || s.stateStore() == null || run == null) return;
        try {
            s.stateStore().saveSnapshot(new RunSnapshot(
                    run.id(), run.workflowId(), run.status(),
                    run.currentStepId(), ctx.getAllOutputs(),
                    run.attemptCount(), run.errorMessage(),
                    run.createdAt(), Instant.now()));
        } catch (Exception e) {
            log.warn("Failed to persist snapshot for run {}: {}", run.id(), e.getMessage());
        }
    }

    private void publish(@Nullable Run run, String stepId, RunEventType type, String detail) {
        String runId = run != null ? run.id() : "";
        for (RunEventListener l : listeners) {
            try {
                l.onEvent(RunEvent.of(runId, type, stepId, detail));
            } catch (Exception e) {
                log.warn("EventListener threw on {}: {}", type, e.getMessage());
            }
        }
    }
}
