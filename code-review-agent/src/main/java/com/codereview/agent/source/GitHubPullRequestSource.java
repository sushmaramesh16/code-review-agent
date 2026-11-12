package com.codereview.agent.source;

import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.PullRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads a real pull request from GitHub's REST API: its title, the changed Java files,
 * each file's diff, and each file's full new content.
 */
@Component
public class GitHubPullRequestSource {

    private static final int MAX_JAVA_FILES = 15;

    private final RestClient github;

    public GitHubPullRequestSource(RestClient.Builder builder,
                                   @Value("${github.api-url:https://api.github.com}") String apiUrl,
                                   @Value("${github.token:}") String token) {
        RestClient.Builder configured = builder
                .baseUrl(apiUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + token);
        }
        this.github = configured.build();
    }

    public Optional<PullRequest> find(String owner, String repo, int number) {
        try {
            JsonNode pr = github.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode files = github.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, number)
                    .retrieve()
                    .body(JsonNode.class);

            List<ChangedFile> changed = new ArrayList<>();
            if (files != null) {
                for (JsonNode file : files) {
                    String path = file.path("filename").asText();
                    boolean removed = "removed".equals(file.path("status").asText());
                    if (!path.endsWith(".java") || removed) {
                        continue;
                    }
                    if (changed.size() >= MAX_JAVA_FILES) {
                        break;
                    }
                    String diff = "--- a/" + path + "\n+++ b/" + path + "\n" + file.path("patch").asText("");
                    changed.add(new ChangedFile(path, diff, fetchContent(file.path("contents_url").asText())));
                }
            }

            String title = owner + "/" + repo + ": "
                    + (pr == null ? "(untitled)" : pr.path("title").asText("(untitled)"));
            return Optional.of(new PullRequest(number, title, changed));

        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new UpstreamServiceException("GitHub request failed: " + e.getMessage(), e);
        }
    }

    /** Downloads one file's full content as plain text, at the pull request's version. */
    private String fetchContent(String contentsUrl) {
        String content = github.get()
                .uri(URI.create(contentsUrl))
                .header("Accept", "application/vnd.github.raw+json")
                .retrieve()
                .body(String.class);
        return content == null ? "" : content;
    }
}
