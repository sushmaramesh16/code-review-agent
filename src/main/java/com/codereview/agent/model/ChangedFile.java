package com.codereview.agent.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One file changed in a pull request: its path, the diff, and the full new content.
 */
public record ChangedFile(String path, String diff, String content) {

    /** Matches a hunk header like "@@ -8,3 +8,17 @@" and captures where the new lines start. */
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /** Counts lines the pull request added. */
    public int addedLines() {
        return addedLineNumbers().size();
    }

    /** Counts lines the pull request deleted ("-" lines inside hunks, not the "---" header). */
    public int deletedLines() {
        int deleted = 0;
        boolean inHunk = false;
        for (String line : diff.lines().toList()) {
            if (HUNK.matcher(line).find()) {
                inHunk = true;
            } else if (inHunk && line.startsWith("-")) {
                deleted++;
            }
        }
        return deleted;
    }

    /** True if the diff has at least one hunk, so we know exactly which lines changed. */
    public boolean hasPatch() {
        return diff.lines().anyMatch(line -> HUNK.matcher(line).find());
    }

    /**
     * Line numbers, in the new version of the file, of every line this pull request added.
     * Walks the diff: "+" lines are new, "-" lines are removed, " " lines are unchanged context.
     */
    public Set<Integer> addedLineNumbers() {
        Set<Integer> added = new LinkedHashSet<>();
        int newLine = -1;
        for (String line : diff.lines().toList()) {
            Matcher hunk = HUNK.matcher(line);
            if (hunk.find()) {
                newLine = Integer.parseInt(hunk.group(1));
                continue;
            }
            if (newLine < 0 || line.startsWith("\\")) {
                continue; // file headers before the first hunk, or "\ No newline at end of file"
            }
            if (line.startsWith("+")) {
                added.add(newLine);
                newLine++;
            } else if (!line.startsWith("-")) {
                newLine++; // unchanged context line
            }
        }
        return added;
    }
}
