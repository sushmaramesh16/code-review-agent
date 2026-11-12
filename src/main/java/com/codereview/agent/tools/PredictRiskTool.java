package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangedFile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks the files inside one pull request so the riskiest get checked first, from 0.0 to 1.0.
 * A simple, explainable heuristic. The trained model scores the WHOLE change instead
 * (see AssessChangeRiskTool), because that is the level its training data was labeled at.
 */
@Component
public class PredictRiskTool implements Tool {

    private static final List<String> SENSITIVE_AREAS =
            List.of("payment", "billing", "auth", "security", "password");

    @Override
    public String name() {
        return "predict_risk";
    }

    @Override
    public String description() {
        return "Estimates how bug-prone one changed file is, from 0.0 (low) to 1.0 (high). "
                + "Args: {\"path\": \"<file path>\"}.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        String path = Tool.requirePath(args);
        ChangedFile file = context.requireFile(path);

        int added = file.addedLines();
        String lowerPath = path.toLowerCase(Locale.ROOT);
        boolean sensitive = SENSITIVE_AREAS.stream().anyMatch(lowerPath::contains);
        boolean controller = lowerPath.contains("controller");

        double risk = Math.min(0.5, added / 20.0);
        if (sensitive) {
            risk += 0.3;
        }
        if (controller) {
            risk += 0.1;
        }
        risk = Math.min(1.0, risk);

        return String.format(Locale.ROOT,
                "risk=%.2f for %s (added lines: %d, sensitive area: %s, controller: %s). "
                        + "File-priority heuristic; the trained model scores the whole change via assess_change_risk.",
                risk, path, added, sensitive ? "yes" : "no", controller ? "yes" : "no");
    }
}
