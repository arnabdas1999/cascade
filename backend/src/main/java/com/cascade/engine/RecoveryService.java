package com.cascade.engine;

import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.model.RunStatus;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.persistence.RunSnapshot;
import com.cascade.persistence.StateStore;
import com.cascade.persistence.entity.WorkflowEntity;
import com.cascade.persistence.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * On startup, scans for runs that were RUNNING when the server last crashed
 * and resumes them from their last persisted step.
 *
 * This is the feature that makes Cascade "durable": kill the server mid-run,
 * restart it, and the run continues from where it left off.
 */
@Component
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final StateStore stateStore;
    private final WorkflowRepository workflowRepo;
    private final WorkflowParser parser;
    private final ExecutionEngine engine;
    private final Scheduler scheduler;
    private final ObjectMapper mapper;

    public RecoveryService(StateStore stateStore,
                           WorkflowRepository workflowRepo,
                           WorkflowParser parser,
                           ExecutionEngine engine,
                           Scheduler scheduler,
                           ObjectMapper mapper) {
        this.stateStore = stateStore;
        this.workflowRepo = workflowRepo;
        this.parser = parser;
        this.engine = engine;
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        List<RunSnapshot> interrupted = stateStore.findByStatus(RunStatus.RUNNING);
        if (interrupted.isEmpty()) {
            log.info("RecoveryService: no interrupted runs found");
            return;
        }
        log.info("RecoveryService: recovering {} interrupted run(s)", interrupted.size());
        for (RunSnapshot snapshot : interrupted) {
            recoverRun(snapshot);
        }
    }

    private void recoverRun(RunSnapshot snapshot) {
        log.info("Recovering run {} for workflow {}", snapshot.runId(), snapshot.workflowId());
        try {
            WorkflowEntity workflowEntity = workflowRepo.findById(snapshot.workflowId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Workflow not found for run " + snapshot.runId()));

            JsonNode defJson = mapper.readTree(workflowEntity.getDefinitionJson());
            WorkflowNode root = parser.parse(defJson);

            // Restore context from snapshot — completed steps' outputs are pre-populated
            ExecutionContext ctx = ExecutionContext.recover(snapshot.contextOutputs());

            // Rehydrate the Run domain object (already in RUNNING state)
            Run run = Run.rehydrate(snapshot);

            scheduler.submit(snapshot.runId(), () -> {
                log.info("Resuming run {} from step {}", run.id(), snapshot.currentStepId());
                NodeResult result = engine.execute(root, run, ctx, stateStore);

                RunSnapshot finalSnapshot;
                if (result.success()) {
                    run.complete(result);
                    finalSnapshot = snapshotOf(run, ctx, RunStatus.COMPLETED);
                } else {
                    run.fail(result.errorMessage());
                    finalSnapshot = snapshotOf(run, ctx, RunStatus.FAILED);
                }
                stateStore.saveSnapshot(finalSnapshot);
                log.info("Recovered run {} finished with status {}", run.id(), run.status());
            });

        } catch (Exception e) {
            log.error("Failed to recover run {}: {}", snapshot.runId(), e.getMessage(), e);
            // Mark it as FAILED so it won't be picked up on the next restart
            stateStore.saveSnapshot(new RunSnapshot(
                    snapshot.runId(), snapshot.workflowId(), RunStatus.FAILED,
                    snapshot.currentStepId(), snapshot.contextOutputs(),
                    snapshot.attemptCount(), "Recovery failed: " + e.getMessage(),
                    snapshot.createdAt(), Instant.now()));
        }
    }

    private RunSnapshot snapshotOf(Run run, ExecutionContext ctx, RunStatus status) {
        return new RunSnapshot(
                run.id(), run.workflowId(), status,
                run.currentStepId(), ctx.getAllOutputs(),
                run.attemptCount(), run.errorMessage(),
                run.createdAt(), Instant.now());
    }
}
