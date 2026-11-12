package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.Finding;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.model.ReviewResult;
import com.codereview.agent.model.ReviewStatus;
import com.codereview.agent.source.DemoPullRequestSource;
import com.codereview.agent.tools.CheckJavaPitfallsTool;
import com.codereview.agent.tools.GetDiffTool;
import com.codereview.agent.tools.GetFileTool;
import com.codereview.agent.tools.ListChangedFilesTool;
import com.codereview.agent.tools.PredictRiskTool;
import com.codereview.agent.tools.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTest {

    private final List<Tool> tools = List.of(new ListChangedFilesTool(), new GetDiffTool(),
            new GetFileTool(), new PredictRiskTool(), new CheckJavaPitfallsTool());
    private final PullRequest demoPr = new DemoPullRequestSource().find(1).orElseThrow();

    @Test
    void findsPlantedBugsAndSkipsLowRiskFile() {
        AgentService agent = new AgentService(tools, new RuleBasedPlanner(), 12);

        ReviewResult result = agent.review(demoPr);

        assertThat(result.status()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(result.findings()).extracting(Finding::rule)
                .containsExactlyInAnyOrder("STRING_EQUALITY", "EMPTY_CATCH", "SYSTEM_OUT");
        assertThat(result.findings()).extracting(Finding::path)
                .noneMatch(path -> path.endsWith("UserUtils.java"));
    }

    @Test
    void stopsAtTheStepLimit() {
        Planner neverFinishes = (pr, history) ->
                AgentAction.callTool("again", "list_changed_files", Map.of());
        AgentService agent = new AgentService(tools, neverFinishes, 3);

        ReviewResult result = agent.review(demoPr);

        assertThat(result.status()).isEqualTo(ReviewStatus.STEP_LIMIT_REACHED);
        assertThat(result.steps()).hasSize(3);
    }

    @Test
    void unknownToolBecomesAnErrorTheAgentCanSee() {
        Planner badThenDone = (pr, history) -> history.isEmpty()
                ? AgentAction.callTool("try something", "delete_repository", Map.of())
                : AgentAction.finish("done", "done");
        AgentService agent = new AgentService(tools, badThenDone, 5);

        ReviewResult result = agent.review(demoPr);

        assertThat(result.steps().get(0).observation()).startsWith("Error: Unknown tool 'delete_repository'");
    }
}
