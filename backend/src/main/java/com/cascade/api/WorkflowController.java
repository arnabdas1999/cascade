package com.cascade.api;

import com.cascade.api.dto.RunResponse;
import com.cascade.api.dto.TriggerRunRequest;
import com.cascade.api.dto.WorkflowRequest;
import com.cascade.api.dto.WorkflowResponse;
import com.cascade.domain.model.WorkflowDefinition;
import com.cascade.engine.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody WorkflowRequest req) {
        WorkflowDefinition def = workflowService.createWorkflow(req.name(), req.definition());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowResponse.from(def));
    }

    @GetMapping
    public List<WorkflowResponse> list() {
        return workflowService.listWorkflows().stream().map(WorkflowResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> get(@PathVariable String id) {
        return workflowService.getWorkflow(id)
                .map(WorkflowResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Trigger a run asynchronously — returns 202 Accepted with the run ID.
     * Clients poll GET /api/runs/{runId} for progress.
     */
    @PostMapping("/{id}/runs")
    public ResponseEntity<Map<String, String>> triggerRun(
            @PathVariable String id,
            @RequestBody(required = false) TriggerRunRequest req) {
        try {
            Map<String, Object> inputs = req != null ? req.inputs() : Map.of();
            String runId = workflowService.triggerRun(id, inputs);
            return ResponseEntity.accepted()
                    .body(Map.of("runId", runId, "status", "PENDING"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/runs")
    public List<RunResponse> listRuns(@PathVariable String id) {
        return workflowService.listRunsForWorkflow(id)
                .stream().map(RunResponse::from).toList();
    }
}
