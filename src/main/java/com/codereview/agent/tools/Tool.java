package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;

import java.util.Map;

/**
 * Something the agent is allowed to do. The planner picks a tool by name;
 * Java code decides what the tool actually does.
 */
public interface Tool {

    String name();

    /** Shown to the LLM so it knows when and how to use the tool. */
    String description();

    /** Runs the tool and returns a plain-text observation for the agent. */
    String execute(Map<String, String> args, ReviewContext context);

    static String requirePath(Map<String, String> args) {
        String path = args.get("path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Missing required argument 'path'.");
        }
        return path;
    }
}
