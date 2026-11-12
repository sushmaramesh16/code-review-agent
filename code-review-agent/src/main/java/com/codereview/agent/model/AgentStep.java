package com.codereview.agent.model;

import java.util.Map;

/** One recorded step of the agent: why it acted, what it called, and what it saw. */
public record AgentStep(int number, String reasoning, String tool,
                        Map<String, String> args, String observation) {
}
