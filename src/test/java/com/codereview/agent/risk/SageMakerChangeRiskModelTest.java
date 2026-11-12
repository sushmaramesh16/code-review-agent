package com.codereview.agent.risk;

import com.codereview.agent.model.ChangeStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Replaces the network call with a fake, so these tests never touch AWS. */
class SageMakerChangeRiskModelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChangeStats stats = new ChangeStats(120, 30, 4, 2, 1, 1.6);

    @Test
    void sendsTheSixFeaturesAndReadsTheScore() {
        AtomicReference<String> sent = new AtomicReference<>();
        SageMakerChangeRiskModel model = new SageMakerChangeRiskModel(mapper, "code-review-risk", body -> {
            sent.set(body);
            return "{\"risk\": [0.8123]}";
        });

        ChangeRisk risk = model.assess(stats);

        assertThat(risk.score()).isEqualTo(0.8123);
        assertThat(risk.source()).isEqualTo("sagemaker:code-review-risk");
        assertThat(sent.get()).contains("\"instances\"", "\"la\":120.0", "\"ent\":1.6");
    }

    @Test
    void fallsBackToTheHeuristicWhenSageMakerFails() {
        SageMakerChangeRiskModel model = new SageMakerChangeRiskModel(mapper, "code-review-risk", body -> {
            throw new IllegalStateException("ExpiredToken");
        });

        ChangeRisk risk = model.assess(stats);

        assertThat(risk.score()).isEqualTo(0.5);
        assertThat(risk.source()).contains("fallback");
    }

    @Test
    void fallsBackOnAnUnexpectedResponse() {
        SageMakerChangeRiskModel model = new SageMakerChangeRiskModel(mapper, "code-review-risk",
                body -> "{\"oops\": true}");

        assertThat(model.assess(stats).source()).contains("fallback");
    }
}
