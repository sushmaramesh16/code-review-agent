package com.codereview.agent.source;

import com.codereview.agent.model.PullRequest;

import java.util.List;
import java.util.Optional;

/**
 * Where pull requests come from. Today: built-in demo data.
 * Next step: a GitHub implementation of this same interface.
 */
public interface PullRequestSource {

    Optional<PullRequest> find(int number);

    List<PullRequest> all();
}
