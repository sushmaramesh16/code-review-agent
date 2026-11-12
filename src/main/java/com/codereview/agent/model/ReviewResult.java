package com.codereview.agent.model;

import java.time.Instant;
import java.util.List;

/**
 * The result of one review.
 *
 * summary    - facts computed by Java from the recorded findings, so it is always accurate.
 * agentNotes - the planner's own closing words. Useful context, but never trusted for facts.
 */
public record ReviewResult(String id, int prNumber, String prTitle, ReviewStatus status,
                           String summary, String agentNotes, List<Finding> findings,
                           List<AgentStep> steps, Instant createdAt) {
}
