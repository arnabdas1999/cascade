package com.cascade.engine;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.ParallelBlock;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.step.ConditionalStep;
import com.cascade.domain.step.DelayStep;
import com.cascade.domain.step.LoggingStepDecorator;
import com.cascade.domain.step.StepResult;
import com.cascade.domain.step.SubWorkflowStep;
import com.cascade.domain.step.TransformStep;
import com.cascade.domain.step.AbstractStep;
import com.cascade.domain.retry.FixedDelayRetry;
import com.cascade.domain.retry.NoRetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Phase3FeaturesTest {

    private ExecutionEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        engine = new ExecutionEngine(List.of());
    }

    // ---- Conditional ----

    @Test
    void conditionalTakesTrueBranch() {
        // step1 produces value=20; condition checks > 10 → true branch
        var seq = new SequentialBlock("root", List.of(
                new TransformStep("step1", "20"),
                new ConditionalStep("decide",
                        "#step1 > 10",
                        new TransformStep("big", "'big-path'"),
                        new TransformStep("small", "'small-path'"))
        ));
        var run = runOf("wf");
        NodeResult result = engine.execute(seq, run, ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKey("big");
        assertThat(result.outputs()).doesNotContainKey("small");
    }

    @Test
    void conditionalTakesFalseBranch() {
        var seq = new SequentialBlock("root", List.of(
                new TransformStep("step1", "5"),
                new ConditionalStep("decide",
                        "#step1 > 10",
                        new TransformStep("big", "'big-path'"),
                        new TransformStep("small", "'small-path'"))
        ));
        NodeResult result = engine.execute(seq, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKey("small");
        assertThat(result.outputs()).doesNotContainKey("big");
    }

    @Test
    void conditionalWithNoFalseBranchReturnsEmpty() {
        var seq = new SequentialBlock("root", List.of(
                new TransformStep("v", "3"),
                new ConditionalStep("decide", "#v > 10",
                        new TransformStep("big", "'big'"), null)
        ));
        NodeResult result = engine.execute(seq, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).doesNotContainKey("big");
    }

    // ---- Parallel ----

    @Test
    void parallelRunsBothBranches() {
        var parallel = new ParallelBlock("fan-out", List.of(
                new DelayStep("branch-a", 10),
                new DelayStep("branch-b", 10)
        ));
        NodeResult result = engine.execute(parallel, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKeys("branch-a", "branch-b");
    }

    @Test
    void parallelFailsIfAnyBranchFails() {
        var parallel = new ParallelBlock("fan-out", List.of(
                new DelayStep("ok-branch", 10),
                new TransformStep("bad-branch", "invalid####expr")
        ));
        NodeResult result = engine.execute(parallel, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isFalse();
    }

    // ---- Retry ----

    @Test
    void stepRetriesAndEventuallySucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);

        var flakyStep = new AbstractStep("flaky",
                new FixedDelayRetry(3, Duration.ofMillis(5))) {
            @Override
            protected StepResult doExecute(ExecutionContext ctx) throws StepException {
                int call = callCount.incrementAndGet();
                if (call < 3) {
                    throw new StepException("flaky", "not ready yet (attempt " + call + ")", true);
                }
                return StepResult.success("succeeded on attempt " + call);
            }
        };

        NodeResult result = engine.execute(flakyStep, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    void nonRetryableStepFailsImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);

        var failStep = new AbstractStep("fail", NoRetry.INSTANCE) {
            @Override
            protected StepResult doExecute(ExecutionContext ctx) throws StepException {
                callCount.incrementAndGet();
                throw new StepException("fail", "non-retryable failure", false);
            }
        };

        NodeResult result = engine.execute(failStep, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isFalse();
        assertThat(callCount.get()).isEqualTo(1); // called exactly once
    }

    // ---- SubWorkflow ----

    @Test
    void subWorkflowStepExecutesEmbeddedTree() {
        var subTree = new SequentialBlock("sub-root", List.of(
                new DelayStep("sub-step1", 5),
                new TransformStep("sub-step2", "42")
        ));
        var step = new SubWorkflowStep("run-sub", subTree, NoRetry.INSTANCE);
        var seq  = new SequentialBlock("root", List.of(step));

        NodeResult result = engine.execute(seq, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        // Sub-step outputs should be in the context
        ExecutionContext ctx = ExecutionContext.empty();
        engine.execute(seq, runOf("wf2"), ctx);
        assertThat(ctx.getOutput("sub-step1")).isPresent();
        assertThat(ctx.getOutput("sub-step2")).isPresent();
    }

    // ---- Decorator ----

    @Test
    void loggingDecoratorTransparentlyWrapsStep() {
        var realStep = new TransformStep("real", "99");
        var decorated = new LoggingStepDecorator(realStep);

        NodeResult result = engine.execute(decorated, runOf("wf"), ExecutionContext.empty());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKey("real");
        assertThat(result.outputs().get("real")).isEqualTo(99);
    }

    // ---- Parser round-trip for new types ----

    @Test
    void parserBuildsConditionalFromJson() throws Exception {
        String json = """
            {
              "root": {
                "type": "conditional",
                "id": "decide",
                "condition": "1 > 0",
                "trueBranch": { "type": "delay", "id": "t", "ms": 1 },
                "falseBranch": { "type": "delay", "id": "f", "ms": 1 }
              }
            }
            """;
        var parser = new WorkflowParser(new StepFactory());
        var root = parser.parse(mapper.readTree(json));
        assertThat(root).isInstanceOf(ConditionalStep.class);
    }

    @Test
    void parserBuildsSubWorkflowFromJson() throws Exception {
        String json = """
            {
              "root": {
                "type": "subworkflow",
                "id": "sub",
                "definition": {
                  "root": { "type": "delay", "id": "inner", "ms": 1 }
                }
              }
            }
            """;
        var parser = new WorkflowParser(new StepFactory());
        var root = parser.parse(mapper.readTree(json));
        assertThat(root).isInstanceOf(SubWorkflowStep.class);
        assertThat(((SubWorkflowStep) root).subTree()).isInstanceOf(DelayStep.class);
    }

    // ---- Helper ----

    private Run runOf(String workflowId) {
        Run run = new Run(workflowId);
        run.start();
        return run;
    }
}
