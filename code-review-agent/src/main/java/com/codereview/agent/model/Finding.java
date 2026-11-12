package com.codereview.agent.model;

/** A problem the agent found, tied to an exact file and line. */
public record Finding(String path, int line, String rule, String message, String suggestion) {
}
