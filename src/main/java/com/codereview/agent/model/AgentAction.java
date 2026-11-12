package com.codereview.agent.model;

import java.util.Map;

/**
 * What the planner wants to do next: call one tool, or finish.
 */
public record AgentAction(String reasoning, String action, String tool,
                          Map<String, String> args, String summary) {

    public static AgentAction callTool(String reasoning, String tool, Map<String, String> args) {
        return new AgentAction(reasoning, "tool", tool, args == null ? Map.of() : Map.copyOf(args), null);
    }

    public static AgentAction finish(String reasoning, String summary) {
        return new AgentAction(reasoning, "finish", null, Map.of(), summary);
    }

    public boolean isFinish() {
        return "finish".equals(action);
    }
}
