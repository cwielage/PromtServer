package com.lafayette.promptserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the Gemini REST API to generate a short summary of a prompt's content.
 * If the API key is not configured the service silently returns empty.
 */
@Slf4j
@Service
public class GeminiService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public GeminiService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.gemini.model:gemini-1.5-flash}") String model) {

        this.apiKey  = apiKey;
        this.model   = model;
        this.enabled = apiKey != null
                && !apiKey.isBlank()
                && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        if (!this.enabled) {
            log.info("GeminiService disabled — set app.gemini.api-key in application.yml to enable auto-summarisation");
        }
    }

    /**
     * Generates a 1-2 sentence summary of the given prompt content.
     *
     * @param promptContent the full text of the AI prompt
     * @return the summary, or empty if the API call fails / is not configured
     */
    public Optional<String> summarize(String promptContent) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            String instruction =
                    "Summarize the following AI prompt in 1-2 concise sentences. " +
                    "Describe what task or behaviour the prompt is designed to achieve. " +
                    "Reply with the summary only, no preamble.\n\n" + promptContent;

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", instruction)))
                    ),
                    "generationConfig", Map.of(
                            "maxOutputTokens", 150,
                            "temperature", 0.3
                    )
            );

            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response != null
                    && response.candidates() != null
                    && !response.candidates().isEmpty()) {

                String text = response.candidates().get(0)
                        .content().parts().get(0).text().trim();
                return Optional.of(text);
            }

        } catch (Exception e) {
            log.warn("Gemini summarisation failed: {}", e.getMessage());
        }

        return Optional.empty();
    }

    // ── Response shape ───────────────────────────────────────────────────────

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
