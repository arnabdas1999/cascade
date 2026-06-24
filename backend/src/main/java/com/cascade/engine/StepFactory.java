package com.cascade.engine;

import com.cascade.domain.node.ParallelBlock;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.domain.retry.ExponentialBackoffRetry;
import com.cascade.domain.retry.FixedDelayRetry;
import com.cascade.domain.retry.NoRetry;
import com.cascade.domain.retry.RetryPolicy;
import com.cascade.domain.step.ConditionalStep;
import com.cascade.domain.step.DelayStep;
import com.cascade.domain.step.HttpStep;
import com.cascade.domain.step.SubWorkflowStep;
import com.cascade.domain.step.TransformStep;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory: builds the right WorkflowNode subtype from a definition JSON node.
 * Also handles recursive sub-workflow inlining for "subworkflow" steps.
 */
@Component
public class StepFactory {

    public WorkflowNode build(JsonNode node) {
        String type = node.path("type").asText("sequential");
        String id   = node.path("id").asText(UUID.randomUUID().toString());

        return switch (type) {
            case "sequential"  -> buildSequential(id, node);
            case "parallel"    -> buildParallel(id, node);
            case "http"        -> buildHttp(id, node);
            case "delay"       -> buildDelay(id, node);
            case "transform"   -> buildTransform(id, node);
            case "conditional" -> buildConditional(id, node);
            case "subworkflow" -> buildSubWorkflow(id, node);
            default -> throw new IllegalArgumentException("Unknown step type: " + type);
        };
    }

    // ---- Block builders ----

    private SequentialBlock buildSequential(String id, JsonNode node) {
        List<WorkflowNode> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) children.add(build(child));
        return new SequentialBlock(id, children);
    }

    private ParallelBlock buildParallel(String id, JsonNode node) {
        List<WorkflowNode> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) children.add(build(child));
        return new ParallelBlock(id, children);
    }

    // ---- Step builders ----

    private HttpStep buildHttp(String id, JsonNode node) {
        String url    = node.path("url").asText();
        String method = node.path("method").asText("GET");
        String body   = node.has("body") ? node.path("body").toString() : null;
        return new HttpStep(id, url, method, body, parseRetry(node));
    }

    private DelayStep buildDelay(String id, JsonNode node) {
        long ms = node.path("ms").asLong(1000);
        return new DelayStep(id, ms, parseRetry(node));
    }

    private TransformStep buildTransform(String id, JsonNode node) {
        String expr = node.path("expr").asText();
        return new TransformStep(id, expr, parseRetry(node));
    }

    private ConditionalStep buildConditional(String id, JsonNode node) {
        String condition = node.path("condition").asText();
        if (condition.isBlank()) throw new IllegalArgumentException(
                "Conditional step '" + id + "' requires a non-blank 'condition' field");

        WorkflowNode trueBranch  = build(node.path("trueBranch"));
        WorkflowNode falseBranch = node.has("falseBranch")
                ? build(node.path("falseBranch")) : null;
        return new ConditionalStep(id, condition, trueBranch, falseBranch);
    }

    /**
     * Inline sub-workflow: the "definition" object is parsed recursively
     * by this same factory — no circular dependency, no DB lookup.
     */
    private SubWorkflowStep buildSubWorkflow(String id, JsonNode node) {
        JsonNode defNode = node.path("definition");
        JsonNode rootNode = defNode.has("root") ? defNode.path("root") : defNode;
        if (rootNode.isMissingNode()) throw new IllegalArgumentException(
                "subworkflow step '" + id + "' must have a 'definition.root' node");
        WorkflowNode subTree = build(rootNode);
        return new SubWorkflowStep(id, subTree, parseRetry(node));
    }

    // ---- Retry parser ----

    private RetryPolicy parseRetry(JsonNode node) {
        JsonNode retry = node.path("retry");
        if (retry.isMissingNode()) return NoRetry.INSTANCE;

        String strategy  = retry.path("strategy").asText("none");
        int maxAttempts  = retry.path("maxAttempts").asInt(1);

        return switch (strategy) {
            case "fixed" -> new FixedDelayRetry(
                    maxAttempts,
                    Duration.ofMillis(retry.path("delayMs").asLong(1000)));
            case "exponential" -> new ExponentialBackoffRetry(
                    maxAttempts,
                    Duration.ofMillis(retry.path("initialDelayMs").asLong(500)),
                    retry.path("multiplier").asDouble(2.0),
                    Duration.ofMillis(retry.path("maxDelayMs").asLong(30_000)));
            default -> NoRetry.INSTANCE;
        };
    }
}
