package com.cascade.engine;

import com.cascade.domain.node.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Interpreter: translates a raw JSON workflow definition into an executable
 * WorkflowNode tree. Delegates per-node construction to StepFactory.
 */
@Component
public class WorkflowParser {

    private final StepFactory stepFactory;

    public WorkflowParser(StepFactory stepFactory) {
        this.stepFactory = stepFactory;
    }

    /**
     * Parse the "root" node of a workflow definition JSON.
     *
     * @param definitionJson the full workflow definition (must contain a "root" field)
     * @return the root WorkflowNode of the executable tree
     */
    public WorkflowNode parse(JsonNode definitionJson) {
        JsonNode root = definitionJson.path("root");
        if (root.isMissingNode()) {
            throw new IllegalArgumentException("Workflow definition must contain a 'root' node");
        }
        return stepFactory.build(root);
    }
}
