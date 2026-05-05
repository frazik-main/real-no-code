package com.realnocode.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realnocode.config.AiProviderProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class LmStudioProviderClient implements AiProviderClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiProviderProperties properties;

    public LmStudioProviderClient(RestTemplate restTemplate, ObjectMapper objectMapper, AiProviderProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.LOCAL;
    }

    @Override
    public String generateHtml(String prompt, String userPrompt, String generateEndpoint) throws Exception {
        String baseUrl = trimTrailingSlash(properties.getLmStudio().getBaseUrl());
        String url = baseUrl + "/v1/chat/completions";

        Map<String, Object> payload = Map.of(
                "model", properties.getLmStudio().getModel(),
                "temperature", 0.2,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        return extractText(response.getBody());
    }

    private String extractText(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IllegalStateException("LM Studio returned empty content.");
        }
        return content;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:1234";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

