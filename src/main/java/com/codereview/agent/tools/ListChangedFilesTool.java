package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import com.codereview.agent.model.ChangedFile;
import org.springframework.stereotype.Component;

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
        return out.toString().trim();
    }
}
