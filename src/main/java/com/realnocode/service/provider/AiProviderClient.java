package com.realnocode.service.provider;

public interface AiProviderClient {

    AiProviderType type();

    String generateHtml(String prompt, String userPrompt, String generateEndpoint) throws Exception;
}

