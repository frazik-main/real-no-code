package com.realnocode.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realnocode.config.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class GeminiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiProviderClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiProviderProperties properties;

    public GeminiProviderClient(RestTemplate restTemplate, ObjectMapper objectMapper, AiProviderProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.GEMINI;
    }

    @Override
    public String generateHtml(String prompt, String userPrompt, String generateEndpoint) throws Exception {
        String apiKey = properties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is missing. Set AI_GEMINI_API_KEY in your .env file.");
        }

        String model = properties.getGemini().getModel();
        String baseUrl = trimTrailingSlash(properties.getGemini().getBaseUrl());
        String url = baseUrl + "/v1beta/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8)
                + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        Exception lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
                return extractText(response.getBody());
            } catch (HttpClientErrorException e) {
                lastException = translateHttpError(e, model);
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("Gemini rate-limited (attempt {}/{}). Waiting {}ms.", attempt, MAX_RETRIES, backoffMs);
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                        continue;
                    }
                }
                // For non-retryable 4xx errors, throw immediately
                throw lastException;
            } catch (HttpServerErrorException e) {
                if (e.getStatusCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                    throw new IllegalStateException("Gemini server error " + e.getStatusCode() + ".", e);
                }
                log.warn("Gemini 503 overloaded (attempt {}/{}). Waiting {}ms.", attempt, MAX_RETRIES, backoffMs);
                lastException = new IllegalStateException(
                        "Gemini is overloaded after " + MAX_RETRIES + " attempts. "
                                + "Try a different model via AI_GEMINI_MODEL in .env, or switch to a different provider.", e);
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw lastException;
            } catch (ResourceAccessException e) {
                log.warn("Gemini transport error (attempt {}/{}): {}", attempt, MAX_RETRIES, compactMessage(e.getMessage()));
                lastException = new IllegalStateException("Gemini network error: " + compactMessage(e.getMessage()), e);
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }
                throw new IllegalStateException(
                        "Gemini connection failed after " + MAX_RETRIES + " attempts. "
                                + "This is usually temporary (e.g. connection reset). "
                                + "Try again in a moment, or switch provider.",
                        e
                );
            }
        }

        throw new IllegalStateException(
                "Gemini rate limit exceeded after " + MAX_RETRIES + " attempts. "
                        + "You have exceeded your free quota. Check your plan at https://ai.google.dev/gemini-api/docs/rate-limits "
                        + "or switch to a different provider.",
                lastException
        );
    }

    private Exception translateHttpError(HttpClientErrorException e, String model) {
        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            return new IllegalStateException(
                    "Gemini rate limit hit (429). You've exceeded the free tier quota. "
                            + "Options: wait a minute, upgrade your Google AI plan, or switch to a different provider.", e);
        }
        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return new IllegalStateException(
                    "Gemini API key is invalid or expired. Check AI_GEMINI_API_KEY in your .env file.", e);
        }
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new IllegalStateException(
                    "Gemini model '" + model + "' was not found. "
                            + "Update AI_GEMINI_MODEL in your .env file (e.g. gemini-2.5-flash).", e);
        }
        return new IllegalStateException(
                "Gemini API error " + e.getStatusCode() + ". Check your API key and model name.", e);
    }

    private String extractText(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini returned no content.");
        }

        String text = StreamSupport.stream(parts.spliterator(), false)
                .map(p -> p.path("text").asText("").strip())
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n"));

        if (text.isBlank()) {
            throw new IllegalStateException("Gemini returned empty text content.");
        }
        return text;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://generativelanguage.googleapis.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String compactMessage(String message) {
        if (message == null || message.isBlank()) {
            return "I/O failure";
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 140 ? compact.substring(0, 140) + "..." : compact;
    }
}

