package com.codereview.agent.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {"prNumber": 1}                                        -> the built-in demo pull request
 * {"owner": "spring-projects", "repo": "...", "prNumber": 42} -> a real GitHub pull request
 */
public record ReviewRequest(
        @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}", message = "must be a valid GitHub owner name") String owner,
        @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}", message = "must be a valid GitHub repository name") String repo,
        @NotNull @Positive Integer prNumber) {

    public boolean isGitHub() {
        return owner != null && repo != null;
    }

    public boolean hasOnlyOneOfOwnerAndRepo() {
        return (owner == null) != (repo == null);
    }
}
