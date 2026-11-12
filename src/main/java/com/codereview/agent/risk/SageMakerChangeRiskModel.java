package com.codereview.agent.risk;

import com.codereview.agent.model.ChangeStats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Asks the trained model, hosted on a SageMaker serverless endpoint, for the change's bug risk.
 * If SageMaker fails for any reason, it falls back to the heuristic instead of failing the review,
 * and says so in the result (graceful degradation).
 */
@Component
@ConditionalOnProperty(name = "risk.model", havingValue = "sagemaker")
public class SageMakerChangeRiskModel implements ChangeRiskModel {

    private static final Logger log = LoggerFactory.getLogger(SageMakerChangeRiskModel.class);

    private final ObjectMapper mapper;
    private final String endpointName;
    private final UnaryOperator<String> invoker;
    private final SageMakerRuntimeClient client;
    private final HeuristicChangeRiskModel fallback = new HeuristicChangeRiskModel();

    @Autowired
    public SageMakerChangeRiskModel(ObjectMapper mapper,
                                    @Value("${risk.sagemaker.endpoint}") String endpointName,
                                    @Value("${risk.sagemaker.region}") String region) {
        this.mapper = mapper;
        this.endpointName = endpointName;
        // Credentials come from the default chain, which includes your `aws login` session.
        this.client = SageMakerRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(30)) // serverless cold starts can take a few seconds
                        .build())
                .build();
        this.invoker = this::invokeEndpoint;
    }

    /** For tests: replaces the network call with a function from request JSON to response JSON. */
    SageMakerChangeRiskModel(ObjectMapper mapper, String endpointName, UnaryOperator<String> invoker) {
        this.mapper = mapper;
        this.endpointName = endpointName;
        this.client = null;
        this.invoker = invoker;
    }

    @Override
    public ChangeRisk assess(ChangeStats stats) {
        try {
            String request = mapper.writeValueAsString(Map.of("instances", List.of(stats.asModelFeatures())));
            JsonNode response = mapper.readTree(invoker.apply(request));
            JsonNode risk = response.path("risk").path(0);
            if (!risk.isNumber()) {
                throw new IllegalStateException("Unexpected response: " + response);
            }
            return new ChangeRisk(risk.asDouble(), "sagemaker:" + endpointName);
        } catch (Exception e) {
            log.warn("SageMaker risk model unavailable, using heuristic: {}", e.getMessage());
            ChangeRisk heuristic = fallback.assess(stats);
            return new ChangeRisk(heuristic.score(), "heuristic fallback (SageMaker unavailable)");
        }
    }

    private String invokeEndpoint(String body) {
        return client.invokeEndpoint(InvokeEndpointRequest.builder()
                        .endpointName(endpointName)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(body))
                        .build())
                .body()
                .asUtf8String();
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
