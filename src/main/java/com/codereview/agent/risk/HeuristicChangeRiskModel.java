package com.codereview.agent.risk;

import com.codereview.agent.model.ChangeStats;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The old rule: more added lines = riskier, capped at 0.5. Used when the trained model is
 * switched off, and as the fallback when SageMaker can't be reached.
 */
@Component
@ConditionalOnProperty(name = "risk.model", havingValue = "heuristic", matchIfMissing = true)
public class HeuristicChangeRiskModel implements ChangeRiskModel {

    @Override
    public ChangeRisk assess(ChangeStats stats) {
        return new ChangeRisk(Math.min(0.5, stats.linesAdded() / 20.0), "heuristic");
    }
}
