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
 * A deterministic planner: list files, ask the model how risky the whole change is, score each
 * file, then check files from the riskiest down. A high-risk change gets every file checked;
 * otherwise the lowest-risk files beyond the review budget are skipped.
 * Needs no LLM, so it is the default and is what the tests use.
 */
@Component
@ConditionalOnProperty(name = "agent.planner", havingValue = "rule", matchIfMissing = true)
public class RuleBasedPlanner implements Planner {

    static final int DEFAULT_CHECK_BUDGET = 8;
    static final double HIGH_CHANGE_RISK = 0.7;
    private static final Pattern RISK = Pattern.compile("(?<!_)risk=([0-9.]+)");
    private static final Pattern CHANGE_RISK = Pattern.compile("change_risk=([0-9.]+)");

    private final int checkBudget;

    public RuleBasedPlanner() {
        this(DEFAULT_CHECK_BUDGET);
    }

    RuleBasedPlanner(int checkBudget) {
        this.checkBudget = checkBudget;
    }

    @Override
    public AgentAction nextAction(PullRequest pullRequest, List<AgentStep> history) {
        if (!called(history, "list_changed_files", null)) {
            return AgentAction.callTool("Start by seeing which files changed.",
                    "list_changed_files", Map.of());
        }

        if (!called(history, "assess_change_risk", null)) {
            return AgentAction.callTool("Ask the trained model how likely this whole change is to introduce a bug.",
                    "assess_change_risk", Map.of());
        }
        double changeRisk = changeRisk(history);
        boolean highRisk = changeRisk >= HIGH_CHANGE_RISK;
        int budget = highRisk ? Integer.MAX_VALUE : checkBudget;

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
            if (called(history, "check_java_pitfalls", path)) {
                checked.add(fileName(path));
            } else if (checked.size() < budget) {
                return AgentAction.callTool(fileName(path) + " has risk " + entry.getValue()
                                + ", the highest not yet checked. Scan its new lines for common Java bugs.",
                        "check_java_pitfalls", Map.of("path", path));
            } else {
                skipped.add(fileName(path));
            }
        }

        String summary = (highRisk ? "Change risk " + changeRisk + " is high, so every file was checked. " : "")
                + "Checked " + checked.size() + " file(s) in order of risk"
                + (checked.isEmpty() ? "" : ": " + String.join(", ", checked)) + ".";
        if (!skipped.isEmpty()) {
            summary += " Skipped " + skipped.size() + " lowest-risk file(s) to stay within the review budget of "
                    + checkBudget + ": " + String.join(", ", skipped) + ".";
        }
        return AgentAction.finish("Every file is scored and the budget of checks is used up or not needed.", summary);
    }

    private static boolean called(List<AgentStep> history, String tool, String path) {
        return history.stream().anyMatch(step -> tool.equals(step.tool())
                && (path == null || path.equals(step.args().get("path"))));
    }

    private static double changeRisk(List<AgentStep> history) {
        for (AgentStep step : history) {
            if ("assess_change_risk".equals(step.tool())) {
                Matcher matcher = CHANGE_RISK.matcher(step.observation());
                if (matcher.find()) {
                    return Double.parseDouble(matcher.group(1));
                }
            }
        }
        return 0.0;
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
