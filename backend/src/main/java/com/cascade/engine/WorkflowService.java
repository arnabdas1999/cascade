package com.cascade.engine;

import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.model.RunStatus;
import com.cascade.domain.model.WorkflowDefinition;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.persistence.RunSnapshot;
import com.cascade.persistence.StateStore;
import com.cascade.persistence.entity.WorkflowEntity;
import com.cascade.persistence.repository.RunSnapshotRepository;
import com.cascade.persistence.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Orchestrates: parse → create run → execute (async) → persist result.
 * Phase 2: execution is async via Scheduler; callers poll GET /api/runs/{id}.
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository workflowRepo;
    private final RunSnapshotRepository snapshotRepo;
    private final StateStore stateStore;
    private final WorkflowParser parser;
    private final ExecutionEngine engine;
    private final CompensationOrchestrator compensator;
    private final Scheduler scheduler;
    private final ObjectMapper mapper;

    public WorkflowService(WorkflowRepository workflowRepo,
                           RunSnapshotRepository snapshotRepo,
                           StateStore stateStore,
                           WorkflowParser parser,
                           ExecutionEngine engine,
                           CompensationOrchestrator compensator,
                           Scheduler scheduler,
                           ObjectMapper mapper) {
        this.workflowRepo = workflowRepo;
        this.snapshotRepo = snapshotRepo;
        this.stateStore = stateStore;
        this.parser = parser;
        this.engine = engine;
        this.compensator = compensator;
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    // ---- Workflow CRUD ----

    public WorkflowDefinition createWorkflow(String name, JsonNode definitionJson) {
        try {
            var def = WorkflowDefinition.create(name, definitionJson);
            var entity = new WorkflowEntity(def.id(), def.name(),
                    mapper.writeValueAsString(definitionJson), def.createdAt());
            workflowRepo.save(entity);
            return def;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save workflow", e);
        }
    }

    public Optional<WorkflowDefinition> getWorkflow(String id) {
        return workflowRepo.findById(id).map(this::toDomain);
    }

    public List<WorkflowDefinition> listWorkflows() {
        return workflowRepo.findAll().stream().map(this::toDomain).toList();
    }

    // ---- Run management ----

    /**
     * Trigger a new run asynchronously.
     * Returns the run ID immediately (status = PENDING); clients poll for completion.
     */
    public String triggerRun(String workflowId, Map<String, Object> inputs) {
        WorkflowEntity entity = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + workflowId));

        Run run = new Run(workflowId);

        // Persist initial PENDING snapshot before we even start
        stateStore.saveSnapshot(new RunSnapshot(
                run.id(), workflowId, RunStatus.PENDING,
                null, inputs, 0, null, run.createdAt(), Instant.now()));

        scheduler.submit(run.id(), () -> executeAsync(run, entity, inputs));

        log.info("Queued run {} for workflow '{}'", run.id(), entity.getName());
        return run.id();
    }

    private void executeAsync(Run run, WorkflowEntity entity, Map<String, Object> inputs) {
        // Transition to RUNNING and persist
        run.start();
        stateStore.saveSnapshot(new RunSnapshot(
                run.id(), run.workflowId(), RunStatus.RUNNING,
                null, inputs, 0, null, run.createdAt(), Instant.now()));

        WorkflowNode root = null;
        ExecutionContext ctx = null;
        try {
            JsonNode defJson = mapper.readTree(entity.getDefinitionJson());
            root = parser.parse(defJson);
            ctx  = new ExecutionContext(inputs);

            log.info("Executing run {} for workflow '{}'", run.id(), entity.getName());
            NodeResult result = engine.execute(root, run, ctx, stateStore);

            if (result.success()) {
                run.complete(result);
                log.info("Run {} completed", run.id());
            } else {
                run.fail(result.errorMessage());
                log.warn("Run {} failed: {}", run.id(), result.errorMessage());
            }
        } catch (Exception e) {
            run.fail(e.getMessage());
            log.error("Run {} threw unexpectedly: {}", run.id(), e.getMessage(), e);
        }

        // Saga compensation: if run failed and we have the tree + context, undo completed steps
        if (run.status() == RunStatus.FAILED && root != null && ctx != null) {
            try {
                compensator.compensate(root, run, ctx, stateStore);
            } catch (Exception e) {
                log.error("Compensation failed for run {}: {}", run.id(), e.getMessage(), e);
            }
        }

        // Persist final state (COMPLETED, FAILED, or COMPENSATED)
        stateStore.saveSnapshot(new RunSnapshot(
                run.id(), run.workflowId(), run.status(),
                run.currentStepId(), ctx != null ? ctx.getAllOutputs() : Map.of(),
                run.attemptCount(), run.errorMessage(),
                run.createdAt(), Instant.now()));
    }

    public Optional<RunSnapshot> getRun(String runId) {
        return stateStore.load(runId);
    }

    public List<RunSnapshot> listRunsForWorkflow(String workflowId) {
        return snapshotRepo.findByWorkflowId(workflowId).stream()
                .map(e -> stateStore.load(e.getRunId()).orElse(null))
                .filter(s -> s != null)
                .toList();
    }

    public List<RunSnapshot> listRunsByStatus(RunStatus status) {
        return stateStore.findByStatus(status);
    }

    // ---- Mapping ----

    private WorkflowDefinition toDomain(WorkflowEntity e) {
        try {
            return new WorkflowDefinition(e.getId(), e.getName(),
                    mapper.readTree(e.getDefinitionJson()), e.getCreatedAt());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize workflow " + e.getId(), ex);
        }
    }
}
