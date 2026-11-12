package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.Finding;
import com.codereview.agent.model.PullRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CheckJavaPitfallsToolTest {

    private final CheckJavaPitfallsTool tool = new CheckJavaPitfallsTool();

    private static final String CONTENT = """
            class A {
              void old() { System.out.println("old"); }
              void a(String s) {
                if (s.equals("x")) {}
                // System.out.println("comment");
                try (FileReader r = new FileReader("f")) {}
                FileReader leak = new FileReader("g");
                try { run(); } catch (Throwable t) { t.printStackTrace(); }
                if ("x".equals(s)) {}
              }
            }
            """;

    private static final String DIFF = """
            --- a/A.java
            +++ b/A.java
            @@ -1,3 +1,11 @@
             class A {
               void old() { System.out.println("old"); }
            +  void a(String s) {
            +    if (s.equals("x")) {}
            +    // System.out.println("comment");
            +    try (FileReader r = new FileReader("f")) {}
            +    FileReader leak = new FileReader("g");
            +    try { run(); } catch (Throwable t) { t.printStackTrace(); }
            +    if ("x".equals(s)) {}
            +  }
             }
            """;

    @Test
    void flagsOnlyNewLinesAndSkipsCommentsAndSafeCode() {
        ReviewContext context = contextWith(new ChangedFile("A.java", DIFF, CONTENT));

        tool.execute(Map.of("path", "A.java"), context);

        assertThat(context.findings())
                .extracting(Finding::line, Finding::rule)
                .containsExactlyInAnyOrder(
                        tuple(4, "NULL_UNSAFE_EQUALS"),
                        tuple(7, "UNCLOSED_RESOURCE"),
                        tuple(8, "BROAD_CATCH"),
                        tuple(8, "PRINT_STACK_TRACE"));
    }

    @Test
    void scansWholeFileWhenNoDiffIsAvailable() {
        ReviewContext context = contextWith(new ChangedFile("A.java", "", CONTENT));

        String observation = tool.execute(Map.of("path", "A.java"), context);

        assertThat(observation).contains("whole file");
        assertThat(context.findings()).extracting(Finding::rule).contains("SYSTEM_OUT");
    }

    private static ReviewContext contextWith(ChangedFile file) {
        return new ReviewContext(new PullRequest(1, "test", List.of(file)));
    }
}
