package com.cascade.engine;

import com.cascade.domain.node.SequentialBlock;
import com.cascade.domain.node.WorkflowNode;
import com.cascade.domain.step.DelayStep;
import com.cascade.domain.step.HttpStep;
import com.cascade.domain.step.TransformStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowParserTest {

    private WorkflowParser parser;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        parser = new WorkflowParser(new StepFactory());
    }

    @Test
    void parsesSequentialWorkflow() throws Exception {
        String json = """
            {
              "name": "demo",
              "root": {
                "type": "sequential",
                "id": "root",
                "children": [
                  { "type": "delay", "id": "wait", "ms": 100 },
                  { "type": "transform", "id": "shape", "expr": "1 + 1" }
                ]
              }
            }
            """;

        WorkflowNode root = parser.parse(mapper.readTree(json));

        assertThat(root).isInstanceOf(SequentialBlock.class);
        SequentialBlock seq = (SequentialBlock) root;
        assertThat(seq.id()).isEqualTo("root");
        assertThat(seq.children()).hasSize(2);
        assertThat(seq.children().get(0)).isInstanceOf(DelayStep.class);
        assertThat(seq.children().get(1)).isInstanceOf(TransformStep.class);
    }

    @Test
    void parsesHttpStep() throws Exception {
        String json = """
            {
              "root": {
                "type": "http",
                "id": "fetch",
                "url": "https://httpbin.org/get",
                "method": "GET"
              }
            }
            """;

        WorkflowNode root = parser.parse(mapper.readTree(json));

        assertThat(root).isInstanceOf(HttpStep.class);
        HttpStep http = (HttpStep) root;
        assertThat(http.url()).isEqualTo("https://httpbin.org/get");
        assertThat(http.method()).isEqualTo("GET");
    }

    @Test
    void throwsOnMissingRoot() throws Exception {
        String json = """
            { "name": "broken" }
            """;
        assertThatThrownBy(() -> parser.parse(mapper.readTree(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root");
    }

    @Test
    void throwsOnUnknownStepType() throws Exception {
        String json = """
            {
              "root": { "type": "unknown_type", "id": "x" }
            }
            """;
        assertThatThrownBy(() -> parser.parse(mapper.readTree(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown step type");
    }
}
