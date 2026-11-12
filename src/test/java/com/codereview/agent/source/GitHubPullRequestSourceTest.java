package com.codereview.agent.source;

import com.codereview.agent.model.ChangedFile;
import com.codereview.agent.model.PullRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Fakes GitHub's responses, so these tests never touch the internet. */
class GitHubPullRequestSourceTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubPullRequestSource source =
            new GitHubPullRequestSource(builder, "https://api.github.com", "");

    @Test
    void loadsOnlyJavaFilesWithDiffAndContent() {
        server.expect(requestTo("https://api.github.com/repos/acme/shop/pulls/7"))
                .andRespond(withSuccess("{\"title\": \"Fix checkout\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/acme/shop/pulls/7/files?per_page=100"))
                .andRespond(withSuccess("""
                        [
                          {"filename": "src/Checkout.java", "status": "modified", "additions": 1, "deletions": 0,
                           "patch": "@@ -1,1 +1,2 @@\\n class Checkout {\\n+  int total;",
                           "contents_url": "https://api.github.com/repos/acme/shop/contents/src/Checkout.java?ref=abc"},
                          {"filename": "README.md", "status": "modified", "additions": 2, "deletions": 1, "patch": "",
                           "contents_url": "https://api.github.com/repos/acme/shop/contents/README.md?ref=abc"}
                        ]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/acme/shop/contents/src/Checkout.java?ref=abc"))
                .andRespond(withSuccess("class Checkout {\n  int total;\n}", MediaType.TEXT_PLAIN));

        PullRequest pr = source.find("acme", "shop", 7).orElseThrow();

        assertThat(pr.title()).isEqualTo("acme/shop: Fix checkout");
        assertThat(pr.files()).extracting(ChangedFile::path).containsExactly("src/Checkout.java");
        assertThat(pr.files().get(0).addedLines()).isEqualTo(1);
        assertThat(pr.files().get(0).content()).contains("int total;");
        // Stats cover ALL changed files (README too), like the model's training data.
        assertThat(pr.stats().linesAdded()).isEqualTo(3);
        assertThat(pr.stats().linesDeleted()).isEqualTo(1);
        assertThat(pr.stats().files()).isEqualTo(2);
        server.verify();
    }

    @Test
    void missingPullRequestIsEmpty() {
        server.expect(requestTo("https://api.github.com/repos/acme/shop/pulls/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(source.find("acme", "shop", 99)).isEmpty();
    }

    @Test
    void rateLimitBecomesUpstreamFailure() {
        server.expect(requestTo("https://api.github.com/repos/acme/shop/pulls/7"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> source.find("acme", "shop", 7))
                .isInstanceOf(UpstreamServiceException.class);
    }
}
