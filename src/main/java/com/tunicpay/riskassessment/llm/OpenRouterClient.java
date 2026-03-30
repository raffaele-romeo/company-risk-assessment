package com.tunicpay.riskassessment.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunicpay.riskassessment.config.AppConfig;
import com.tunicpay.riskassessment.exception.DataParseException;
import com.tunicpay.riskassessment.exception.UpstreamApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final AppConfig.OpenRouter config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenRouterClient(AppConfig appConfig, HttpClient httpClient, ObjectMapper objectMapper) {
        this.config = appConfig.getOpenrouter();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String chatCompletion(String systemPrompt, String userPrompt) {
        try {
            var body = Map.of(
                    "model", config.getModel(),
                    "temperature", config.getTemperature(),
                    "seed", config.getSeed(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - start;

            log.info("OpenRouter call completed in {}ms, status={}, model={}", duration, response.statusCode(), config.getModel());

            if (response.statusCode() != 200) {
                log.error("OpenRouter returned status {}: {}", response.statusCode(), response.body());
                throw new UpstreamApiException("OpenRouter", response.statusCode(), response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (UpstreamApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DataParseException("OpenRouter call failed", e);
        }
    }

    public <T> T chatCompletionWithRetry(String systemPrompt, String userPrompt, Function<String, T> processor) {
        try {
            String response = chatCompletion(systemPrompt, userPrompt);
            return processor.apply(response);
        } catch (Exception e) {
            log.warn("First LLM attempt failed, retrying with corrective prompt: {}", e.getMessage());
            String corrective = userPrompt + "\n\nYour previous response was not valid. The error was: "
                    + e.getMessage() + ". Please try again following the rules exactly.";
            String response = chatCompletion(systemPrompt, corrective);
            return processor.apply(response);
        }
    }

    public String getModel() {
        return config.getModel();
    }
}
