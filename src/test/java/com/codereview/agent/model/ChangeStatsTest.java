package com.codereview.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ChangeStatsTest {

    @Test
    void computesKameiChangeMetrics() {
        ChangeStats stats = ChangeStats.of(List.of(
                new ChangeStats.FileChurn("core/src/A.java", 3, 1),
                new ChangeStats.FileChurn("core/src/B.java", 1, 0),
                new ChangeStats.FileChurn("docs/README.md", 4, 0)));

        assertThat(stats.linesAdded()).isEqualTo(8);
        assertThat(stats.linesDeleted()).isEqualTo(1);
        assertThat(stats.files()).isEqualTo(3);
        assertThat(stats.directories()).isEqualTo(2);   // core/src, docs
        assertThat(stats.subsystems()).isEqualTo(2);    // core, docs
        // churn per file: 4, 1, 4 of 9 total -> -(2 * 4/9 * log2(4/9) + 1/9 * log2(1/9))
        double expected = -(2 * (4.0 / 9) * log2(4.0 / 9) + (1.0 / 9) * log2(1.0 / 9));
        assertThat(stats.entropy()).isCloseTo(expected, within(1e-9));
        assertThat(stats.asModelFeatures()).containsOnlyKeys("la", "ld", "nf", "nd", "ns", "ent");
    }

    @Test
    void entropyOfOneFileIsZero() {
        ChangeStats stats = ChangeStats.of(List.of(new ChangeStats.FileChurn("A.java", 10, 5)));

        assertThat(stats.entropy()).isEqualTo(0.0);
        assertThat(stats.directories()).isEqualTo(1);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
