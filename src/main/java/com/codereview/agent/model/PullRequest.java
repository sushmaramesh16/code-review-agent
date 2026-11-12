package com.codereview.agent.model;

import java.util.List;
import java.util.Optional;

/**
 * A pull request: its Java files to review, plus size/spread stats for the WHOLE change.
 * Stats can include non-Java files (docs, configs) because the risk model was trained on
 * whole commits, while only Java files get reviewed.
 */
public record PullRequest(int number, String title, List<ChangedFile> files, ChangeStats stats) {

    /** Convenience constructor: derives the stats from the given files' diffs. */
    public PullRequest(int number, String title, List<ChangedFile> files) {
        this(number, title, files, ChangeStats.of(files.stream()
                .map(f -> new ChangeStats.FileChurn(f.path(), f.addedLines(), f.deletedLines()))
                .toList()));
    }

    public Optional<ChangedFile> file(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst();
    }
}
