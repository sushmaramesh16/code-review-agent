package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.Finding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Scans one file for common Java mistakes, but only on lines this pull request added,
 * so the author is never blamed for code they didn't touch. This is the only tool that
 * records findings, so every finding is backed by a concrete new line of code.
 */
@Component
public class CheckJavaPitfallsTool implements Tool {

    private record Rule(String id, Predicate<String> matches, String message, String suggestion) {
    }

    private static final Pattern TRY_WITH_RESOURCES = Pattern.compile("\\btry\\s*\\(");

    private static final List<Rule> RULES = List.of(
            new Rule("STRING_EQUALITY",
                    regex("(==|!=)\\s*\"|\"\\s*(==|!=)"),
                    "A String is compared with == or !=, which checks whether two references point to "
                            + "the same object, not whether the text is the same.",
                    "Compare contents instead, e.g. \"PAID\".equals(status) or Objects.equals(a, b)."),
            new Rule("NULL_UNSAFE_EQUALS",
                    regex("\\b[a-z]\\w*(?:\\(\\))?\\.equals\\(\\s*\""),
                    "equals() is called on a variable that might be null, which throws a NullPointerException.",
                    "Put the constant first, e.g. \"PAID\".equals(status), or use Objects.equals(a, b)."),
            new Rule("EMPTY_CATCH",
                    regex("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}"),
                    "The exception is caught and silently ignored, so failures disappear without a trace.",
                    "Log the exception, rethrow it, or handle it explicitly."),
            new Rule("BROAD_CATCH",
                    regex("catch\\s*\\(\\s*(?:final\\s+)?(?:Exception|Throwable|RuntimeException)\\s+\\w+\\s*\\)"),
                    "A very broad exception type is caught, which can hide unrelated bugs.",
                    "Catch the specific exceptions this code can actually throw."),
            new Rule("UNCLOSED_RESOURCE",
                    regex("new\\s+(?:FileInputStream|FileOutputStream|FileReader|FileWriter|"
                            + "BufferedReader|BufferedWriter|Scanner)\\s*\\(")
                            .and(line -> !TRY_WITH_RESOURCES.matcher(line).find()),
                    "A file or stream is opened outside try-with-resources, so it may never be closed.",
                    "Open it in try-with-resources, e.g. try (var reader = new FileReader(path)) { ... }."),
            new Rule("PRINT_STACK_TRACE",
                    regex("\\.printStackTrace\\(\\s*\\)"),
                    "printStackTrace() writes to standard error and bypasses the application's logging.",
                    "Log it instead, e.g. log.error(\"Payment failed\", e)."),
            new Rule("SYSTEM_OUT",
                    regex("System\\.(out|err)\\.print"),
                    "System.out/err is used for logging, which bypasses log levels and log collection.",
                    "Use a logger such as SLF4J, e.g. log.info(\"Fetching order {}\", id).")
    );

    private static Predicate<String> regex(String pattern) {
        return Pattern.compile(pattern).asPredicate();
    }

    @Override
    public String name() {
        return "check_java_pitfalls";
    }

    @Override
    public String description() {
        return "Scans the lines a pull request added to one file for common Java bugs and bad practices, "
                + "and records each finding. Args: {\"path\": \"<file path>\"}.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        String path = Tool.requirePath(args);
        ChangedFile file = context.requireFile(path);
        List<String> lines = file.content().lines().toList();

        boolean onlyNewLines = file.hasPatch();
        Set<Integer> addedLines = file.addedLineNumbers();

        List<Finding> found = new ArrayList<>();
        int scanned = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i);
            if ((onlyNewLines && !addedLines.contains(lineNumber)) || isComment(line)) {
                continue;
            }
            scanned++;
            for (Rule rule : RULES) {
                if (rule.matches().test(line)) {
                    Finding finding = new Finding(path, lineNumber, rule.id(), rule.message(), rule.suggestion());
                    found.add(finding);
                    context.addFinding(finding);
                }
            }
        }

        String scope = onlyNewLines
                ? "Scanned " + scanned + " new line(s) in " + path + "."
                : "No diff was available, so scanned the whole file " + path + ".";
        if (found.isEmpty()) {
            return scope + " No issues found.";
        }
        StringBuilder out = new StringBuilder(scope + " Found " + found.size() + " issue(s):\n");
        for (Finding f : found) {
            out.append("- line ").append(f.line())
               .append(" [").append(f.rule()).append("] ")
               .append(f.message()).append('\n');
        }
        return out.toString().trim();
    }

    private static boolean isComment(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }
}
