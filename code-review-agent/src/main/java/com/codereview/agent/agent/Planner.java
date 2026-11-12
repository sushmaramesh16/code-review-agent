package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.AgentStep;
import com.codereview.agent.model.PullRequest;

import java.util.List;

/**
 * The agent's "brain": looks at everything that happened so far and proposes the next action.
 * It only proposes. AgentService decides whether and how to run it.
 */
@FunctionalInterface
public interface Planner {

    AgentAction nextAction(PullRequest pullRequest, List<AgentStep> history);
}
