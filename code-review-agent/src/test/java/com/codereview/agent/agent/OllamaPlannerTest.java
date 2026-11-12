package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests only the parsing of model replies, so no LLM needs to be running. */
class OllamaPlannerTest {

    private final OllamaPlanner planner =
            new OllamaPlanner(List.of(), new ObjectMapper(), "http://localhost:11434", "test-model");

    @Test
    void parsesToolCall() {
        AgentAction action = planner.parse(
                "{\"reasoning\":\"look\",\"action\":\"tool\",\"tool\":\"get_diff\",\"args\":{\"path\":\"A.java\"}}");

        assertThat(action.isFinish()).isFalse();
        assertThat(action.tool()).isEqualTo("get_diff");
        assertThat(action.args()).containsEntry("path", "A.java");
    }

    @Test
    void parsesFinish() {
        AgentAction action = planner.parse("{\"reasoning\":\"done\",\"action\":\"finish\",\"summary\":\"All good.\"}");

        assertThat(action.isFinish()).isTrue();
        assertThat(action.summary()).isEqualTo("All good.");
    }

    @Test
    void invalidReplyBecomesInvalidResponseTool() {
        assertThat(planner.parse("not json at all").tool()).isEqualTo("invalid_response");
    }
}
