package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetFileTool implements Tool {

    @Override
    public String name() {
        return "get_file";
    }

    @Override
    public String description() {
        return "Shows the full new version of one file with line numbers. Args: {\"path\": \"<file path>\"}.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        List<String> lines = context.requireFile(Tool.requirePath(args)).content().lines().toList();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            out.append(i + 1).append(": ").append(lines.get(i)).append('\n');
        }
        return out.toString().trim();
    }
}
