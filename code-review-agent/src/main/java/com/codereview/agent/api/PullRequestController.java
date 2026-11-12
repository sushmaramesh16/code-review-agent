package com.codereview.agent.api;

import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.source.PullRequestSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/pull-requests")
public class PullRequestController {

    public record PullRequestSummary(int number, String title, List<String> files) {
    }

    private final PullRequestSource pullRequests;

    public PullRequestController(PullRequestSource pullRequests) {
        this.pullRequests = pullRequests;
    }

    @GetMapping
    public List<PullRequestSummary> list() {
        return pullRequests.all().stream()
                .map(pr -> new PullRequestSummary(pr.number(), pr.title(),
                        pr.files().stream().map(ChangedFile::path).toList()))
                .toList();
    }

    @GetMapping("/{number}")
    public PullRequest get(@PathVariable("number") int number) {
        return pullRequests.find(number).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pull request #" + number + " was not found."));
    }
}
