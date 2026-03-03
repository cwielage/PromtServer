package com.lafayette.promptserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the Gemini REST API for summarisation and prompt optimisation.
 * Uses the systemInstruction + contents format for best model performance.
 * If the API key is not configured the service silently returns empty.
 */
@Slf4j
@Service
public class GeminiService {

    // ── System instructions ───────────────────────────────────────────────────

    private static final String SUMMARY_SYSTEM =
            "You are a technical documentation specialist for an AI prompt library.\n" +
            "Your job is to write concise, informative summaries that help users instantly\n" +
            "understand what a prompt does without reading the full text.\n\n" +
            "A good summary:\n" +
            "- States the primary purpose and use case in the first sentence\n" +
            "- Mentions the AI persona or role if one is defined in the prompt\n" +
            "- Notes any key output format, tone, or special constraints\n" +
            "- Is 2–3 sentences maximum\n" +
            "- Matches the language of the original prompt (German prompt → German summary)\n\n" +
            "Reply with the summary text only — no bullet points, no headers, no markdown, no preamble.";

    private static final String OPTIMIZE_SYSTEM =
            "You are a world-class AI prompt engineer specializing in crafting high-performance\n" +
            "prompts for large language models.\n\n" +
            "Rewrite the given prompt by systematically applying these improvements:\n\n" +
            "1. ROLE  — Open with a precise \"You are ...\" statement that establishes expertise and context.\n" +
            "2. TASK  — State the objective explicitly and unambiguously.\n" +
            "3. CONTEXT — Add relevant background, scope, or constraints the model needs to know.\n" +
            "4. STEPS — For complex tasks, break the work into numbered reasoning steps.\n" +
            "5. OUTPUT FORMAT — Specify structure, length, tone, and format of the expected response.\n" +
            "6. GUARDRAILS — Include one or two constraints to keep the output focused and high-quality.\n\n" +
            "Hard rules:\n" +
            "- Return ONLY the optimized prompt text — nothing else.\n" +
            "- Do NOT wrap the output in code blocks, quotes, or markdown.\n" +
            "- Do NOT add explanations, commentary, or a preamble before or after.\n" +
            "- Preserve the original language (German stays German, English stays English).\n" +
            "- Keep all [Variable] placeholders exactly as they appear in the original.\n" +
            "- Preserve the original intent — only improve clarity, structure, and effectiveness.";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final RestClient restClient;
    private final String apiKey;
    private final String model;      // gemini-2.5-pro  — used for optimisation
    private final String fastModel;  // gemini-2.5-flash — used for summaries
    private final boolean enabled;

    public GeminiService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.gemini.model:gemini-2.5-pro}") String model,
            @Value("${app.gemini.fast-model:gemini-2.5-flash}") String fastModel) {

        this.apiKey     = apiKey;
        this.model      = model;
        this.fastModel  = fastModel;
        this.enabled    = apiKey != null
                && !apiKey.isBlank()
                && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");

        // gemini-2.5-pro (thinking model) can take 60-120 s — extend default timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);   // 15 s to establish connection
        factory.setReadTimeout(180_000);     // 3 min to receive full response

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        if (this.enabled) {
            log.info("GeminiService enabled — optimize-model: {}, summary-model: {}", model, fastModel);
        } else {
            log.info("GeminiService disabled — set app.gemini.api-key to enable AI features");
        }
    }

    // ── Public methods ────────────────────────────────────────────────────────

    /**
     * Generates a 2-3 sentence summary using the fast model (no thinking overhead).
     */
    public Optional<String> summarize(String promptContent) {
        if (!enabled) return Optional.empty();

        String userMessage = "Write a summary for this AI prompt:\n\n" + promptContent;

        Optional<String> result = call(fastModel, SUMMARY_SYSTEM, userMessage, 300, 0.2);
        result.ifPresent(s -> log.info("Gemini summary: {}", s));
        return result;
    }

    /**
     * Asks Gemini to rewrite and optimise the prompt using prompt-engineering best practices.
     * Returns the improved text only — does NOT save anything.
     */
    /**
     * Asks Gemini to rewrite and optimise the prompt using the full thinking model.
     * Returns the improved text only — does NOT save anything.
     */
    public Optional<String> optimize(String promptContent) {
        if (!enabled) return Optional.empty();

        String userMessage = "Optimize this AI prompt:\n\n" + promptContent;

        return call(model, OPTIMIZE_SYSTEM, userMessage, 4096, 0.4);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a single-turn request to Gemini using the systemInstruction + contents format.
     *
     * @param modelId           the model to use (fast-model or full model)
     * @param systemInstruction the system-level behaviour instruction
     * @param userMessage       the user-turn message
     * @param maxTokens         maximum output tokens
     * @param temperature       generation temperature
     */
    private Optional<String> call(String modelId, String systemInstruction, String userMessage,
                                  int maxTokens, double temperature) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemInstruction))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", userMessage))
                            )
                    ),
                    "generationConfig", Map.of(
                            "maxOutputTokens", maxTokens,
                            "temperature", temperature
                    )
            );

            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", modelId, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            // Thinking models (gemini-2.5-pro) may return multiple candidates/parts,
            // some with null content or thought-only parts (no text). Iterate safely.
            if (response != null && response.candidates() != null) {
                for (Candidate candidate : response.candidates()) {
                    if (candidate.content() == null || candidate.content().parts() == null) continue;
                    for (Part part : candidate.content().parts()) {
                        if (part != null && part.text() != null && !part.text().isBlank()) {
                            return Optional.of(part.text().trim());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Gemini call failed (model={}): {}", model, e.getMessage());
        }

        return Optional.empty();
    }

    // ── Response shape ────────────────────────────────────────────────────────

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
