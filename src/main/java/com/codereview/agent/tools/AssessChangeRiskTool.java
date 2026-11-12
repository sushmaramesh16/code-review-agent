package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangeStats;
import com.codereview.agent.risk.ChangeRisk;
import com.codereview.agent.risk.ChangeRiskModel;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/** Asks the risk model how likely the whole pull request is to introduce a bug. */
@Component
public class AssessChangeRiskTool implements Tool {

    private final ChangeRiskModel model;

    public AssessChangeRiskTool(ChangeRiskModel model) {
        this.model = model;
    }

    @Override
    public String name() {
        return "assess_change_risk";
    }

    @Override
    public String description() {
        return "Asks a model trained on ~75,000 real Apache Java commits how likely this whole change is to "
                + "introduce a bug (0.0-1.0). High risk (0.7 or more) means every file deserves a check. No arguments.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        ChangeStats stats = context.pullRequest().stats();
        ChangeRisk risk = model.assess(stats);
        context.setChangeRisk(risk);
        return String.format(Locale.ROOT,
                "change_risk=%.2f (source: %s). Features: la=%d, ld=%d, nf=%d, nd=%d, ns=%d, ent=%.2f.",
                risk.score(), risk.source(), stats.linesAdded(), stats.linesDeleted(), stats.files(),
                stats.directories(), stats.subsystems(), stats.entropy());
    }
}
