package com.codereview.agent.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Size and spread of a whole change, computed exactly like the ApacheJIT dataset
 * the risk model was trained on (Kamei et al. change metrics). Matching the training
 * definitions matters: different definitions would silently feed the model wrong numbers.
 *
 * linesAdded / linesDeleted - raw line counts across all changed files (la, ld)
 * files                     - number of changed files (nf)
 * directories               - distinct parent directories (nd)
 * subsystems                - distinct top-level folders (ns)
 * entropy                   - how evenly the changed lines are spread across files,
 *                             in bits (log base 2), not normalized (ent)
 */
public record ChangeStats(int linesAdded, int linesDeleted, int files, int directories,
                          int subsystems, double entropy) {

    /** Lines added and deleted in one file. */
    public record FileChurn(String path, int added, int deleted) {
    }

    public static ChangeStats of(List<FileChurn> files) {
        int added = 0;
        int deleted = 0;
        Set<String> directories = new HashSet<>();
        Set<String> subsystems = new HashSet<>();

        for (FileChurn file : files) {
            added += file.added();
            deleted += file.deleted();
            String path = file.path();
            int lastSlash = path.lastIndexOf('/');
            int firstSlash = path.indexOf('/');
            directories.add(lastSlash < 0 ? "" : path.substring(0, lastSlash));
            subsystems.add(firstSlash < 0 ? "" : path.substring(0, firstSlash));
        }

        double total = added + deleted;
        double entropy = 0.0;
        if (total > 0) {
            for (FileChurn file : files) {
                double churn = file.added() + file.deleted();
                if (churn > 0) {
                    double share = churn / total;
                    entropy -= share * (Math.log(share) / Math.log(2));
                }
            }
        }

        return new ChangeStats(added, deleted, files.size(), directories.size(), subsystems.size(), entropy);
    }

    /** The features in the order and names the model was trained with. */
    public Map<String, Double> asModelFeatures() {
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("la", (double) linesAdded);
        features.put("ld", (double) linesDeleted);
        features.put("nf", (double) files);
        features.put("nd", (double) directories);
        features.put("ns", (double) subsystems);
        features.put("ent", entropy);
        return features;
    }
}
