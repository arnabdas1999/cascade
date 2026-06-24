package com.cascade.domain.model;

import com.cascade.domain.event.RunEvent;
import com.cascade.domain.event.RunEventType;
import com.cascade.domain.node.NodeResult;
import com.cascade.persistence.RunSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * State machine for a single workflow execution.
 * Guards all transitions: illegal moves throw IllegalStateException.
 */
public final class Run {

    private String id;
    private final String workflowId;
    private RunStatus status;
    private String currentStepId;
    private NodeResult result;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private int attemptCount;

    final List<RunEvent> events = new ArrayList<>();

    public Run(String workflowId) {
        this.id = UUID.randomUUID().toString();
        this.workflowId = workflowId;
        this.status = RunStatus.PENDING;
        this.createdAt = Instant.now();
        this.attemptCount = 0;
        addEvent(RunEventType.RUN_STARTED, null, "Run created");
    }

    /** Private constructor used only by rehydrate(). */
    private Run(String id, String workflowId, RunStatus status, String currentStepId,
                Instant createdAt, int attemptCount) {
        this.id = id;
        this.workflowId = workflowId;
        this.status = status;
        this.currentStepId = currentStepId;
        this.createdAt = createdAt;
        this.attemptCount = attemptCount;
    }

    /**
     * Reconstruct a Run from a persisted snapshot for crash recovery.
     * Starts the recovered run in RUNNING state so execution continues immediately.
     */
    public static Run rehydrate(RunSnapshot snapshot) {
        Run run = new Run(
                snapshot.runId(),
                snapshot.workflowId(),
                RunStatus.RUNNING,
                snapshot.currentStepId(),
                snapshot.createdAt() != null ? snapshot.createdAt() : Instant.now(),
                snapshot.attemptCount());
        run.startedAt = Instant.now();
        run.addEvent(RunEventType.RUN_STARTED, null, "Recovered from snapshot");
        return run;
    }

    // ---- state transitions ----

    public void start() {
        requireStatus(RunStatus.PENDING);
        status = RunStatus.RUNNING;
        startedAt = Instant.now();
        addEvent(RunEventType.RUN_STARTED, null, null);
    }

    public void stepStarted(String stepId) {
        requireStatus(RunStatus.RUNNING);
        currentStepId = stepId;
        addEvent(RunEventType.STEP_STARTED, stepId, null);
    }

    public void stepCompleted(String stepId) {
        requireStatus(RunStatus.RUNNING);
        addEvent(RunEventType.STEP_COMPLETED, stepId, null);
    }

    public void stepFailed(String stepId, String error) {
        addEvent(RunEventType.STEP_FAILED, stepId, error);
    }

    public void stepRetrying(String stepId, int attempt) {
        addEvent(RunEventType.STEP_RETRYING, stepId, "attempt " + attempt);
        attemptCount++;
    }

    public void complete(NodeResult result) {
        requireStatus(RunStatus.RUNNING);
        status = RunStatus.COMPLETED;
        this.result = result;
        completedAt = Instant.now();
        addEvent(RunEventType.RUN_COMPLETED, null, null);
    }

    public void fail(String error) {
        if (status == RunStatus.PENDING || status == RunStatus.RUNNING) {
            status = RunStatus.FAILED;
            errorMessage = error;
            completedAt = Instant.now();
            addEvent(RunEventType.RUN_FAILED, currentStepId, error);
        }
    }

    public void startCompensation() {
        requireStatus(RunStatus.FAILED);
        status = RunStatus.COMPENSATING;
        addEvent(RunEventType.RUN_COMPENSATING, null, null);
    }

    public void compensated() {
        requireStatus(RunStatus.COMPENSATING);
        status = RunStatus.COMPENSATED;
        completedAt = Instant.now();
        addEvent(RunEventType.RUN_COMPENSATED, null, null);
    }

    // ---- accessors ----

    public String id() { return id; }
    public String workflowId() { return workflowId; }
    public RunStatus status() { return status; }
    public String currentStepId() { return currentStepId; }
    public NodeResult result() { return result; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public int attemptCount() { return attemptCount; }
    public List<RunEvent> events() { return Collections.unmodifiableList(events); }

    void addEvent(RunEventType type, String stepId, String detail) {
        events.add(RunEvent.of(id, type, stepId, detail));
    }

    private void requireStatus(RunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Run %s: expected status %s but was %s".formatted(id, expected, status));
        }
    }
}
