package com.codereview.agent.agent;

import com.codereview.agent.model.AgentAction;
import com.codereview.agent.model.AgentStep;
import com.codereview.agent.model.PullRequest;
import com.codereview.agent.tools.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A planner where a real LLM (running locally in Ollama, free) decides each step.
 * Turn it on with agent.planner=ollama.
 */
@Component
@ConditionalOnProperty(name = "agent.planner", havingValue = "ollama")
public class OllamaPlanner implements Planner {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String model;
    private final String systemPrompt;

    public OllamaPlanner(List<Tool> tools, ObjectMapper mapper,
                         @Value("${ollama.base-url}") String baseUrl,
                         @Value("${ollama.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
        this.model = model;
        this.systemPrompt = buildSystemPrompt(tools);
    }

    @Override
    public AgentAction nextAction(PullRequest pullRequest, List<AgentStep> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content",
                "Review pull request #" + pullRequest.number() + ": \"" + pullRequest.title()
                        + "\". Find real problems, then finish with a short summary."));
        for (AgentStep step : history) {
            messages.add(Map.of("role", "assistant", "content", toJson(step)));
            messages.add(Map.of("role", "user", "content",
                    "Observation from " + step.tool() + ":\n" + step.observation()));
        }

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", messages,
                "stream", false,
                "format", "json",
                "options", Map.of("temperature", 0));

        JsonNode response = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        String content = response == null ? "" : response.path("message").path("content").asText("");
        return parse(content);
    }

    /** Turns the model's JSON reply into an action. Bad replies become an error the agent can see. */
    AgentAction parse(String content) {
        JsonNode node;
        try {
            node = mapper.readTree(content);
        } catch (JsonProcessingException e) {
            node = null;
        }
        if (node == null || !node.isObject()) {
            return AgentAction.callTool("The model did not return a JSON object.", "invalid_response", Map.of());
        }

        String reasoning = node.path("reasoning").asText("");
        String action = node.path("action").asText("");
        if ("finish".equalsIgnoreCase(action)) {
            return AgentAction.finish(reasoning, node.path("summary").asText("Review finished."));
        }

        String tool = node.path("tool").asText("");
        if (tool.isBlank() && !action.isBlank() && !"tool".equalsIgnoreCase(action)) {
            tool = action; // some models put the tool name in "action"
        }
        Map<String, String> args = new LinkedHashMap<>();
        node.path("args").fields().forEachRemaining(e ->
                args.put(e.getKey(), e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));

        return AgentAction.callTool(reasoning, tool.isBlank() ? "invalid_response" : tool, args);
    }

    private String toJson(AgentStep step) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("reasoning", step.reasoning() == null ? "" : step.reasoning());
        json.put("action", "tool");
        json.put("tool", step.tool());
        json.put("args", step.args());
        try {
            return mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize agent step", e);
        }
    }

    private static String buildSystemPrompt(List<Tool> tools) {
        StringBuilder toolList = new StringBuilder();
        for (Tool tool : tools) {
            toolList.append("- ").append(tool.name()).append(": ").append(tool.description()).append('\n');
        }
        return """
                You are a careful code review agent for Java pull requests.
                You work one step at a time. At each step, call exactly one tool, or finish.

                Tools:
                %s
                Reply with only one JSON object, in one of these two shapes:
                {"reasoning": "why this step", "action": "tool", "tool": "<tool name>", "args": {"path": "<file path>"}}
                {"reasoning": "why you are done", "action": "finish", "summary": "<2-3 sentence review summary>"}

                Guidelines:
                - Start with list_changed_files.
                - Use predict_risk to decide which files deserve a closer look.
                - Run check_java_pitfalls on files from the riskiest down. It only reports issues on lines
                  the pull request added, and it is the only tool that records findings.
                - Never claim a problem that a tool did not report.
                - Your summary is shown as notes next to a summary the system computes itself,
                  so don't restate counts of issues or files; describe what matters most instead.
                - Finish once the risky files have been checked.
                """.formatted(toolList);
    }
}
