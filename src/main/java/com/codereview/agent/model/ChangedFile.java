package com.codereview.agent.model;

/**
 * One file changed in a pull request: its path, the diff, and the full new content.
 */
public record ChangedFile(String path, String diff, String content) {

    /** Counts lines the pull request added ("+" lines, not the "+++" header). */
    public int addedLines() {
        return (int) diff.lines()
                .filter(line -> line.startsWith("+") && !line.startsWith("+++"))
                .count();
    }
}
