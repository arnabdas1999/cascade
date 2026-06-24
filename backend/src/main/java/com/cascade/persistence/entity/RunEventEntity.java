package com.cascade.persistence.entity;

import com.cascade.domain.event.RunEventType;
import jakarta.persistence.*;

import java.time.Instant;

/** Immutable audit log row — one row per state change, never updated. */
@Entity
@Table(name = "run_events")
public class RunEventEntity {

    @Id
    private String id;

    @Column(name = "run_id", nullable = false)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private RunEventType eventType;

    @Column(name = "step_id")
    private String stepId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(columnDefinition = "TEXT")
    private String detail;

    public RunEventEntity() {}

    public RunEventEntity(String id, String runId, RunEventType eventType,
                          String stepId, Instant timestamp, String detail) {
        this.id = id;
        this.runId = runId;
        this.eventType = eventType;
        this.stepId = stepId;
        this.timestamp = timestamp;
        this.detail = detail;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public RunEventType getEventType() { return eventType; }
    public String getStepId() { return stepId; }
    public Instant getTimestamp() { return timestamp; }
    public String getDetail() { return detail; }
}
