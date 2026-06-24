package com.cascade.engine;

import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.step.DelayStep;
import com.cascade.domain.step.TransformStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionEngineTest {

    private ExecutionEngine engine;

    @BeforeEach
    void setup() {
        engine = new ExecutionEngine(List.of()); // no listeners in unit tests
    }

    @Test
    void executesDelayStep() {
        var delay = new DelayStep("wait", 10);
        var run = new Run("wf-1");
        run.start();
        var ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(delay, run, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKey("wait");
    }

    @Test
    void executesSequentialWorkflow() {
        var seq = new SequentialBlock("root", List.of(
                new DelayStep("step1", 10),
                new TransformStep("step2", "1 + 1")
        ));
        var run = new Run("wf-2");
        run.start();
        var ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(seq, run, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsKey("step1");
        assertThat(result.outputs()).containsKey("step2");
        assertThat(result.outputs().get("step2")).isEqualTo(2);
    }

    @Test
    void failsOnBadExpression() {
        var step = new TransformStep("bad", "this.is.not.valid.spel####");
        var run = new Run("wf-3");
        run.start();
        var ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(step, run, ctx);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("bad");
    }

    @Test
    void shortCircuitsSequenceOnFailure() {
        var seq = new SequentialBlock("root", List.of(
                new TransformStep("step1", "bad####expression"),
                new DelayStep("step2", 10) // should NOT execute
        ));
        var run = new Run("wf-4");
        run.start();
        var ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(seq, run, ctx);

        assertThat(result.success()).isFalse();
        assertThat(ctx.getAllOutputs()).doesNotContainKey("step2");
    }

    @Test
    void transformCanReadPriorStepOutput() {
        // step1 outputs a value; step2 reads it via SpEL
        var seq = new SequentialBlock("root", List.of(
                new DelayStep("step1", 1),
                new TransformStep("step2", "#step1['delayMs']")
        ));
        var run = new Run("wf-5");
        run.start();
        var ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(seq, run, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.outputs().get("step2")).isEqualTo(1L);
    }
}
