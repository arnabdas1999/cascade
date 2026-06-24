package com.cascade.persistence;

import com.cascade.domain.model.Run;
import com.cascade.domain.model.RunStatus;
import com.cascade.domain.model.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 store — purely in-memory.
 * Phase 2 will replace this with JPA implementations backed by Postgres.
 */
@Component
public class InMemoryWorkflowStore {

    private final ConcurrentHashMap<String, WorkflowDefinition> workflows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Run> runs = new ConcurrentHashMap<>();

    public WorkflowDefinition saveWorkflow(WorkflowDefinition def) {
        workflows.put(def.id(), def);
        return def;
    }

    public Optional<WorkflowDefinition> findWorkflow(String id) {
        return Optional.ofNullable(workflows.get(id));
    }

    public List<WorkflowDefinition> listWorkflows() {
        return List.copyOf(workflows.values());
    }

    public Run saveRun(Run run) {
        runs.put(run.id(), run);
        return run;
    }

    public Optional<Run> findRun(String id) {
        return Optional.ofNullable(runs.get(id));
    }

    public List<Run> findRunsByStatus(RunStatus status) {
        return runs.values().stream()
                .filter(r -> r.status() == status)
                .toList();
    }

    public List<Run> listRunsForWorkflow(String workflowId) {
        return runs.values().stream()
                .filter(r -> r.workflowId().equals(workflowId))
                .toList();
    }
}
