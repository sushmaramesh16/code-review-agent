package com.codereview.agent.risk;

/** How likely a whole change is to introduce a bug (0.0 to 1.0), and where that number came from. */
public record ChangeRisk(double score, String source) {
}
