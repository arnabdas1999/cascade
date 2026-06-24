package com.cascade.persistence;

import com.cascade.domain.model.RunStatus;
import com.cascade.persistence.entity.RunSnapshotEntity;
import com.cascade.persistence.repository.RunSnapshotRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JpaStateStore implements StateStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RunSnapshotRepository repo;
    private final ObjectMapper mapper;

    public JpaStateStore(RunSnapshotRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public void saveSnapshot(RunSnapshot snapshot) {
        RunSnapshotEntity entity = repo.findById(snapshot.runId())
                .orElse(new RunSnapshotEntity());

        entity.setRunId(snapshot.runId());
        entity.setWorkflowId(snapshot.workflowId());
        entity.setStatus(snapshot.status());
        entity.setCurrentStepId(snapshot.currentStepId());
        entity.setAttemptCount(snapshot.attemptCount());
        entity.setErrorMessage(snapshot.errorMessage());
        entity.setUpdatedAt(Instant.now());

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(snapshot.createdAt() != null ? snapshot.createdAt() : Instant.now());
        }

        try {
            entity.setContextJson(mapper.writeValueAsString(snapshot.contextOutputs()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize context for run " + snapshot.runId(), e);
        }

        repo.save(entity);
    }

    @Override
    public Optional<RunSnapshot> load(String runId) {
        return repo.findById(runId).map(this::toDomain);
    }

    @Override
    public List<RunSnapshot> findByStatus(RunStatus status) {
        return repo.findByStatus(status).stream().map(this::toDomain).toList();
    }

    private RunSnapshot toDomain(RunSnapshotEntity e) {
        Map<String, Object> ctx = Map.of();
        if (e.getContextJson() != null && !e.getContextJson().isBlank()) {
            try {
                ctx = mapper.readValue(e.getContextJson(), MAP_TYPE);
            } catch (Exception ex) {
                // corrupt context; return empty — recovery will fail gracefully
            }
        }
        return new RunSnapshot(
                e.getRunId(), e.getWorkflowId(), e.getStatus(),
                e.getCurrentStepId(), ctx, e.getAttemptCount(),
                e.getErrorMessage(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
