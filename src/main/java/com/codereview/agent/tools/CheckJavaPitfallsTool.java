package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scans one file for common Java mistakes. This is the only tool that records findings,
 * so every finding in a review is backed by a concrete line of code.
 */
@Component
public class CheckJavaPitfallsTool implements Tool {

    private record Rule(String id, Pattern pattern, String message, String suggestion) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("STRING_EQUALITY",
                    Pattern.compile("(==|!=)\\s*\"|\"\\s*(==|!=)"),
                    "A String is compared with == or !=, which checks whether two references point to "
                            + "the same object, not whether the text is the same.",
                    "Compare contents instead, e.g. \"PAID\".equals(status) or Objects.equals(a, b)."),
            new Rule("EMPTY_CATCH",
                    Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
                    "The exception is caught and silently ignored, so failures disappear without a trace.",
                    "Log the exception, rethrow it, or handle it explicitly."),
            new Rule("SYSTEM_OUT",
                    Pattern.compile("System\\.(out|err)\\.print"),
                    "System.out/err is used for logging, which bypasses log levels and log collection.",
                    "Use a logger such as SLF4J, e.g. log.info(\"Fetching order {}\", id).")
    );

    @Override
    public String name() {
        return "check_java_pitfalls";
    }

    @Override
    public String description() {
        return "Scans one file for common Java bugs and bad practices and records each finding. "
                + "Args: {\"path\": \"<file path>\"}.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        String path = Tool.requirePath(args);
        ChangedFile file = context.requireFile(path);
        List<String> lines = file.content().lines().toList();

        List<Finding> found = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (Rule rule : RULES) {
                if (rule.pattern().matcher(lines.get(i)).find()) {
                    Finding finding = new Finding(path, i + 1, rule.id(), rule.message(), rule.suggestion());
                    found.add(finding);
                    context.addFinding(finding);
                }
            }
        }

        if (found.isEmpty()) {
            return "No issues found in " + path + ".";
        }
        StringBuilder out = new StringBuilder("Found " + found.size() + " issue(s) in " + path + ":\n");
        for (Finding f : found) {
            out.append("- line ").append(f.line())
               .append(" [").append(f.rule()).append("] ")
               .append(f.message()).append('\n');
        }
        return out.toString().trim();
    }
}
