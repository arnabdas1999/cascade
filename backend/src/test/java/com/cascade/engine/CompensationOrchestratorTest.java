package com.cascade.engine;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.model.Run;
import com.cascade.domain.model.RunStatus;
import com.cascade.domain.node.NodeResult;
import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.node.Step;
import com.cascade.domain.retry.NoRetry;
import com.cascade.domain.step.AbstractStep;
import com.cascade.domain.step.StepResult;
import com.cascade.domain.step.TransformStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4: verifies that when a run fails, completed steps are compensated
 * in reverse order (saga pattern).
 */
class CompensationOrchestratorTest {

    private ExecutionEngine engine;
    private CompensationOrchestrator compensator;

    @BeforeEach
    void setup() {
        engine      = new ExecutionEngine(List.of());
        compensator = new CompensationOrchestrator(List.of());
    }

    @Test
    void compensatesCompletedStepsInReverseOrder() {
        List<String> compensationOrder = new ArrayList<>();

        var step1 = trackingStep("step1", compensationOrder, false);
        var step2 = trackingStep("step2", compensationOrder, false);
        var step3 = trackingStep("step3", compensationOrder, false);
        var failStep = failingStep("fail");

        var seq = new SequentialBlock("root", List.of(step1, step2, step3, failStep));

        Run run = new Run("wf");
        run.start();
        ExecutionContext ctx = ExecutionContext.empty();

        NodeResult result = engine.execute(seq, run, ctx);
        assertThat(result.success()).isFalse();

        run.fail(result.errorMessage());
        compensator.compensate(seq, run, ctx, null);

        assertThat(run.status()).isEqualTo(RunStatus.COMPENSATED);
        // steps 1-3 ran; fail step did not record output → not compensated
        // compensation should be step3 → step2 → step1 (reverse)
        assertThat(compensationOrder).containsExactly("step3", "step2", "step1");
    }

    @Test
    void onlyCompensatesStepsThatActuallyRan() {
        List<String> compensated = new ArrayList<>();

        var step1    = trackingStep("step1", compensated, false);
        var failStep = failingStep("failStep");
        var step3    = trackingStep("step3", compensated, false); // never runs

        var seq = new SequentialBlock("root", List.of(step1, failStep, step3));

        Run run = new Run("wf");
        run.start();
        ExecutionContext ctx = ExecutionContext.empty();

        engine.execute(seq, run, ctx);
        run.fail("step failed");
        compensator.compensate(seq, run, ctx, null);

        // step3 never ran so it's not compensated
        assertThat(compensated).containsExactly("step1");
        assertThat(compensated).doesNotContain("step3");
    }

    @Test
    void runTransitionsToCompensatedState() {
        var seq = new SequentialBlock("root", List.of(
                new TransformStep("s1", "1"),
                failingStep("boom")
        ));

        Run run = new Run("wf");
        run.start();
        ExecutionContext ctx = ExecutionContext.empty();

        engine.execute(seq, run, ctx);
        run.fail("boom");

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);

        compensator.compensate(seq, run, ctx, null);

        assertThat(run.status()).isEqualTo(RunStatus.COMPENSATED);
    }

    @Test
    void compensationContinuesEvenIfOneStepCompensationThrows() {
        List<String> compensated = new ArrayList<>();

        var step1 = trackingStep("step1", compensated, false);
        var step2 = trackingStep("step2", compensated, true);  // compensate() throws
        var step3 = trackingStep("step3", compensated, false);
        var fail  = failingStep("fail");

        var seq = new SequentialBlock("root", List.of(step1, step2, step3, fail));

        Run run = new Run("wf");
        run.start();
        ExecutionContext ctx = ExecutionContext.empty();

        engine.execute(seq, run, ctx);
        run.fail("fail");
        compensator.compensate(seq, run, ctx, null); // must not throw

        // step3 and step1 should be compensated; step2 threw but orchestrator continued
        assertThat(compensated).contains("step3", "step1");
        assertThat(run.status()).isEqualTo(RunStatus.COMPENSATED);
    }

    // ---- Helpers ----

    private Step trackingStep(String id, List<String> log, boolean compensateThrows) {
        return new AbstractStep(id, NoRetry.INSTANCE) {
            @Override
            protected StepResult doExecute(ExecutionContext ctx) {
                return StepResult.success("done");
            }

            @Override
            public void compensate(ExecutionContext ctx) {
                if (compensateThrows) throw new RuntimeException("compensation error for " + id);
                log.add(id);
            }
        };
    }

    private Step failingStep(String id) {
        return new AbstractStep(id, NoRetry.INSTANCE) {
            @Override
            protected StepResult doExecute(ExecutionContext ctx) throws StepException {
                throw new StepException(id, "deliberate failure", false);
            }
        };
    }
}
