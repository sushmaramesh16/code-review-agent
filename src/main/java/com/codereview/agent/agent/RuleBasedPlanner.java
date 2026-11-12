package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.AgentStep;
import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.PullRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic planner: list files, score each file's risk, check the risky ones, finish.
 * Needs no LLM, so it is the default and is what the tests use.
 */
@Component
@ConditionalOnProperty(name = "agent.planner", havingValue = "rule", matchIfMissing = true)
public class RuleBasedPlanner implements Planner {

    static final double RISK_THRESHOLD = 0.3;
    private static final Pattern RISK = Pattern.compile("risk=([0-9.]+)");

    @Override
    public AgentAction nextAction(PullRequest pullRequest, List<AgentStep> history) {
        if (!called(history, "list_changed_files", null)) {
            return AgentAction.callTool("Start by seeing which files changed.",
                    "list_changed_files", Map.of());
        }

        for (ChangedFile file : pullRequest.files()) {
            if (!called(history, "predict_risk", file.path())) {
                return AgentAction.callTool("Estimate how risky " + fileName(file.path())
                                + " is before reading it closely.",
                        "predict_risk", Map.of("path", file.path()));
            }
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(riskScores(history).entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));

        List<String> checked = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, Double> entry : ranked) {
            String path = entry.getKey();
            if (entry.getValue() < RISK_THRESHOLD) {
                skipped.add(fileName(path));
                continue;
            }
            if (!called(history, "check_java_pitfalls", path)) {
                return AgentAction.callTool(fileName(path) + " has risk " + entry.getValue()
                                + ", the highest not yet checked. Scan it for common Java bugs.",
                        "check_java_pitfalls", Map.of("path", path));
            }
            checked.add(fileName(path));
        }

        String summary = "Checked " + checked.size() + " higher-risk file(s)"
                + (checked.isEmpty() ? "" : ": " + String.join(", ", checked))
                + ". Skipped " + skipped.size() + " low-risk file(s)"
                + (skipped.isEmpty() ? "" : ": " + String.join(", ", skipped)) + ".";
        return AgentAction.finish("Every file is scored and every higher-risk file has been checked.", summary);
    }

    private static boolean called(List<AgentStep> history, String tool, String path) {
        return history.stream().anyMatch(step -> tool.equals(step.tool())
                && (path == null || path.equals(step.args().get("path"))));
    }

    private static Map<String, Double> riskScores(List<AgentStep> history) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (AgentStep step : history) {
            if ("predict_risk".equals(step.tool())) {
                Matcher matcher = RISK.matcher(step.observation());
                if (matcher.find()) {
                    scores.put(step.args().get("path"), Double.parseDouble(matcher.group(1)));
                }
            }
        }
        return scores;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
