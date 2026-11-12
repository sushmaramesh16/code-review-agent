package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangeStats;
import com.codereview.agent.model.ChangedFile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class ListChangedFilesTool implements Tool {

    @Override
    public String name() {
        return "list_changed_files";
    }

    @Override
    public String description() {
        return "Lists the files changed in the pull request and how many lines each one adds. No arguments.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        StringBuilder out = new StringBuilder("Changed files:\n");
        for (ChangedFile file : context.pullRequest().files()) {
            out.append("- ").append(file.path())
               .append(" (+").append(file.addedLines()).append(" lines)\n");
        }
        ChangeStats stats = context.pullRequest().stats();
        out.append(String.format(Locale.ROOT,
                "Whole change: +%d/-%d lines across %d file(s) in %d director(ies) and %d top-level folder(s), "
                        + "entropy %.2f bits.",
                stats.linesAdded(), stats.linesDeleted(), stats.files(), stats.directories(),
                stats.subsystems(), stats.entropy()));
        return out.toString().trim();
    }
}
