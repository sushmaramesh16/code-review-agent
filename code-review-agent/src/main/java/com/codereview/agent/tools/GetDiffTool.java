package com.codereview.agent.tools;

import com.codereview.agent.agent.ReviewContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetDiffTool implements Tool {

    @Override
    public String name() {
        return "get_diff";
    }

    @Override
    public String description() {
        return "Shows exactly what changed in one file. Args: {\"path\": \"<file path>\"}.";
    }

    @Override
    public String execute(Map<String, String> args, ReviewContext context) {
        return context.requireFile(Tool.requirePath(args)).diff();
    }
}
