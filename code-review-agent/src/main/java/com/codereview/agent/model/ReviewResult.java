package com.codereview.agent.model;

import java.time.Instant;
import java.util.List;

public record ReviewResult(String id, int prNumber, String prTitle, ReviewStatus status,
                           String summary, List<Finding> findings, List<AgentStep> steps,
                           Instant createdAt) {
}
