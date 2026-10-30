package com.resumatch.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Lightweight wrapper around the public Gemini REST API.
 * No external AI libraries — pure JDK + Jackson.
 *
 * The API key is loaded from the env var GEMINI_API_KEY (with a sensible
 * fallback for local dev). If the key is missing or invalid, all methods
 * degrade to a deterministic stub so the UI still works.
 */
@Service
public class GeminiService {
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    @Value("${resumatch.gemini.api-key:${GEMINI_API_KEY:}}")
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Generate a job description from a title + a few skills.
     * Returns the raw text response, or a stub if the API is unavailable.
     */
    public String suggestJobDescription(String title, java.util.List<String> skills, String employmentType) {
        String stub = "We are hiring a " + title + ".\n\n" +
                "About the role:\n" +
                "You will work on a small, fast-moving team and own features end-to-end. " +
                "Expect a healthy mix of design, code, and collaboration.\n\n" +
                "What you'll do:\n" +
                "- Build and ship production-quality features\n" +
                "- Work closely with product and design to scope and iterate\n" +
                "- Write clean, well-tested code and review your peers'\n\n" +
                "What we're looking for:\n" +
                "- Solid grasp of: " + String.join(", ", skills) + "\n" +
                "- Strong fundamentals and a learning mindset\n" +
                "- Clear written communication\n\n" +
                "Nice to have:\n" +
                "- Open-source contributions or side projects\n" +
                "- Internship or prior project experience\n\n" +
                "What we offer:\n" +
                "- Mentorship from senior engineers\n" +
                "- Flexible hours and a respectful, low-ego culture\n" +
                "- " + (employmentType == null ? "Full-time" : employmentType) + " role with growth opportunities";

        if (!isConfigured()) {
            log.warn("GEMINI_API_KEY not set; returning stub description.");
            return stub;
        }

        String prompt = "You are a recruiting copywriter. Write a clear, professional, 200-300 word job description for a " +
                safe(title) + " role (" + safe(employmentType) + "). " +
                "Required skills: " + String.join(", ", skills == null ? java.util.List.of() : skills) + ". " +
                "Structure the response in these sections, each as a short paragraph or bullet list: " +
                "About the role, What you'll do, What we're looking for, Nice to have, What we offer. " +
                "Avoid hype words, avoid emojis, and do not include any salary information. " +
                "Output ONLY the job description text, no analysis or commentary before or after.";

        try {
            String body = json.writeValueAsString(Map.of(
                    "contents", java.util.List.of(Map.of(
                            "parts", java.util.List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "temperature", 0.6,
                            "maxOutputTokens", 900,
                            "candidateCount", 1)));

            HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Gemini returned {} — falling back to stub. Body: {}", resp.statusCode(),
                        resp.body() == null ? "" : resp.body().substring(0, Math.min(300, resp.body().length())));
                return stub;
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            String out = text.isMissingNode() ? "" : text.asText("");
            return out.isBlank() ? stub : out.trim();
        } catch (Exception e) {
            log.warn("Gemini call failed, falling back to stub: {}", e.getMessage());
            return stub;
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
