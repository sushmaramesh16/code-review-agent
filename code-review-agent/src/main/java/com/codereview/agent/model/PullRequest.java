package com.codereview.agent.model;

import java.util.List;
import java.util.Optional;

public record PullRequest(int number, String title, List<ChangedFile> files) {

    public Optional<ChangedFile> file(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst();
    }
}
