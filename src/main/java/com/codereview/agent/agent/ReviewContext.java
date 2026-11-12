package com.codereview.agent.agent;

import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.Finding;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.risk.ChangeRisk;

import java.util.ArrayList;
import java.util.List;

/** Shared state for one review: the pull request being reviewed and the findings so far. */
public class ReviewContext {

    private final PullRequest pullRequest;
    private final List<Finding> findings = new ArrayList<>();
    private ChangeRisk changeRisk;

    public ReviewContext(PullRequest pullRequest) {
        this.pullRequest = pullRequest;
    }

    public PullRequest pullRequest() {
        return pullRequest;
    }

    public ChangedFile requireFile(String path) {
        return pullRequest.file(path).orElseThrow(() ->
                new IllegalArgumentException("File '" + path + "' is not part of this pull request."));
    }

    public void addFinding(Finding finding) {
        if (!findings.contains(finding)) {
            findings.add(finding);
        }
    }

    public void setChangeRisk(ChangeRisk changeRisk) {
        this.changeRisk = changeRisk;
    }

    /** Null until the agent calls assess_change_risk. */
    public ChangeRisk changeRisk() {
        return changeRisk;
    }

    public List<Finding> findings() {
        return List.copyOf(findings);
    }
}
