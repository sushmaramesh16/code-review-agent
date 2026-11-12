package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.AgentStep;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.model.ReviewResult;
import com.codereview.agent.model.ReviewStatus;
import com.codereview.agent.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The agent loop. The planner proposes the next action; this class checks it,
 * runs the tool, records what happened, and enforces a step limit.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final Map<String, Tool> tools;
    private final Planner planner;
    private final int maxSteps;

    public AgentService(List<Tool> tools, Planner planner,
                        @Value("${agent.max-steps:12}") int maxSteps) {
        this.tools = tools.stream().collect(Collectors.toMap(
                Tool::name, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        this.planner = planner;
        this.maxSteps = maxSteps;
    }

    public ReviewResult review(PullRequest pullRequest) {
        ReviewContext context = new ReviewContext(pullRequest);
        List<AgentStep> steps = new ArrayList<>();
        String summary = null;

        for (int i = 1; i <= maxSteps; i++) {
            AgentAction action = planner.nextAction(pullRequest, List.copyOf(steps));
            if (action.isFinish()) {
                summary = action.summary();
                break;
            }

            String observation = runTool(action, context);
            AgentStep step = new AgentStep(steps.size() + 1, action.reasoning(),
                    action.tool(), action.args(), observation);
            steps.add(step);
            log.info("Step {}: {} {}", step.number(), step.tool(), step.args());
        }

        ReviewStatus status = summary == null ? ReviewStatus.STEP_LIMIT_REACHED : ReviewStatus.COMPLETED;
        if (summary == null) {
            summary = "Stopped after reaching the limit of " + maxSteps + " steps.";
        }

        return new ReviewResult(UUID.randomUUID().toString(), pullRequest.number(), pullRequest.title(),
                status, summary, context.findings(), steps, Instant.now());
    }

    private String runTool(AgentAction action, ReviewContext context) {
        Tool tool = tools.get(action.tool());
        if (tool == null) {
            return "Error: Unknown tool '" + action.tool() + "'. Available tools: " + tools.keySet();
        }
        try {
            return tool.execute(action.args(), context);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }
}
