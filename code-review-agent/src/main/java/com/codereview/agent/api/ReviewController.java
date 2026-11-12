package com.codereview.agent.api;

import com.codereview.agent.agent.AgentService;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.model.ReviewResult;
import com.codereview.agent.source.GitHubPullRequestSource;
import com.codereview.agent.source.PullRequestSource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final PullRequestSource demoPullRequests;
    private final GitHubPullRequestSource gitHub;
    private final AgentService agent;
    private final ReviewStore store;

    public ReviewController(PullRequestSource demoPullRequests, GitHubPullRequestSource gitHub,
                            AgentService agent, ReviewStore store) {
        this.demoPullRequests = demoPullRequests;
        this.gitHub = gitHub;
        this.agent = agent;
        this.store = store;
    }

    /**
     * Starts a review: 201 Created, 400 if the body is invalid,
     * 404 if the PR doesn't exist, 502 if GitHub fails.
     */
    @PostMapping
    public ResponseEntity<ReviewResult> createReview(@Valid @RequestBody ReviewRequest request) {
        if (request.hasOnlyOneOfOwnerAndRepo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide both owner and repo for a GitHub pull request, or neither for the demo.");
        }

        Optional<PullRequest> found = request.isGitHub()
                ? gitHub.find(request.owner(), request.repo(), request.prNumber())
                : demoPullRequests.find(request.prNumber());

        PullRequest pullRequest = found.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pull request #" + request.prNumber() + " was not found."));

        ReviewResult result = agent.review(pullRequest);
        store.save(result);
        return ResponseEntity.created(URI.create("/reviews/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public ReviewResult getReview(@PathVariable("id") String id) {
        return store.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Review " + id + " was not found."));
    }
}
