package com.cascade.persistence.entity;

import com.cascade.domain.model.RunStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * The single row that tracks a run's current state.
 * Updated after every step — the source of crash-recovery truth.
 */
@Entity
@Table(name = "run_snapshots")
public class RunSnapshotEntity {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "current_step_id")
    private String currentStepId;

    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson; // JSON of Map<String, Object>

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RunSnapshotEntity() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public String getCurrentStepId() { return currentStepId; }
    public void setCurrentStepId(String currentStepId) { this.currentStepId = currentStepId; }

    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
