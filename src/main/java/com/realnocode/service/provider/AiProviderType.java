package com.realnocode.service.provider;

public enum AiProviderType {
    COPILOT("copilot"),
    GEMINI("gemini"),
    LOCAL("local");

    private final String value;

    AiProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AiProviderType fromValue(String rawValue, AiProviderType fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        String normalized = rawValue.trim().toLowerCase();
        return switch (normalized) {
            case "copilot" -> COPILOT;
            case "gemini" -> GEMINI;
            case "local", "lmstudio", "lm-studio" -> LOCAL;
            default -> fallback;
        };
    }
}

