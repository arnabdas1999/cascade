package com.cascade.api;

import com.cascade.api.dto.RunResponse;
import com.cascade.domain.model.RunStatus;
import com.cascade.engine.WorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final WorkflowService workflowService;

    public RunController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/{runId}")
    public ResponseEntity<RunResponse> get(@PathVariable String runId) {
        return workflowService.getRun(runId)
                .map(RunResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<RunResponse> listByStatus(@RequestParam(required = false) RunStatus status) {
        if (status != null) {
            return workflowService.listRunsByStatus(status)
                    .stream().map(RunResponse::from).toList();
        }
        return List.of();
    }
}
