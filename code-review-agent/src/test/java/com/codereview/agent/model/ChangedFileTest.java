package com.codereview.agent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangedFileTest {

    @Test
    void findsAddedLineNumbersAcrossHunks() {
        String diff = """
                --- a/A.java
                +++ b/A.java
                @@ -1,4 +1,4 @@
                 a
                -b
                +B
                 c
                @@ -10,2 +10,3 @@
                 x
                +y
                 z
                """;
        ChangedFile file = new ChangedFile("A.java", diff, "");

        assertThat(file.hasPatch()).isTrue();
        assertThat(file.addedLineNumbers()).containsExactly(2, 11);
        assertThat(file.addedLines()).isEqualTo(2);
    }

    @Test
    void noHunksMeansNoPatch() {
        ChangedFile file = new ChangedFile("A.java", "--- a/A.java\n+++ b/A.java\n", "class A {}");

        assertThat(file.hasPatch()).isFalse();
        assertThat(file.addedLineNumbers()).isEmpty();
    }
}
