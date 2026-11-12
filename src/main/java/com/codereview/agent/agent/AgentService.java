package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.AgentStep;
import com.codereview.agent.model.Finding;
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
import java.util.Objects;
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
        String agentNotes = null;

        for (int i = 1; i <= maxSteps; i++) {
            AgentAction action = planner.nextAction(pullRequest, List.copyOf(steps));
            if (action.isFinish()) {
                agentNotes = action.summary() == null ? "" : action.summary();
                break;
            }

            String observation = runTool(action, context);
            AgentStep step = new AgentStep(steps.size() + 1, action.reasoning(),
                    action.tool(), action.args(), observation);
            steps.add(step);
            log.info("Step {}: {} {}", step.number(), step.tool(), step.args());
        }

        ReviewStatus status = agentNotes == null ? ReviewStatus.STEP_LIMIT_REACHED : ReviewStatus.COMPLETED;
        if (agentNotes == null) {
            agentNotes = "Stopped after reaching the limit of " + maxSteps + " steps.";
        }

        List<Finding> findings = context.findings();
        return new ReviewResult(UUID.randomUUID().toString(), pullRequest.number(), pullRequest.title(),
                status, groundedSummary(pullRequest, findings, steps), agentNotes, findings, steps, Instant.now());
    }

    /**
     * Builds the summary from what the tools actually recorded, never from the model's words.
     * Example: "Found 4 issue(s) in 2 file(s): PaymentService.java (3), OrderController.java (1).
     *           Checked 3 of 3 changed file(s)."
     */
    static String groundedSummary(PullRequest pullRequest, List<Finding> findings, List<AgentStep> steps) {
        long checked = steps.stream()
                .filter(step -> "check_java_pitfalls".equals(step.tool()))
                .filter(step -> !step.observation().startsWith("Error"))
                .map(step -> step.args().get("path"))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        String coverage = "Checked " + checked + " of " + pullRequest.files().size() + " changed file(s).";

        if (findings.isEmpty()) {
            return "No issues found. " + coverage;
        }

        Map<String, Long> perFile = findings.stream().collect(Collectors.groupingBy(
                finding -> fileName(finding.path()), LinkedHashMap::new, Collectors.counting()));
        String breakdown = perFile.entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));

        return "Found " + findings.size() + " issue(s) in " + perFile.size() + " file(s): "
                + breakdown + ". " + coverage;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
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
