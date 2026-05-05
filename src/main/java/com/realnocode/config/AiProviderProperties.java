package com.realnocode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProviderProperties {

    private String defaultProvider = "copilot";
    private long timeoutSeconds = 300;
    private final Copilot copilot = new Copilot();
    private final Gemini gemini = new Gemini();
    private final LmStudio lmStudio = new LmStudio();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Copilot getCopilot() {
        return copilot;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public LmStudio getLmStudio() {
        return lmStudio;
    }

    public static class Copilot {
        private String command = "copilot";

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }
    }

    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-2.0-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class LmStudio {
        private String baseUrl = "http://localhost:1234";
        private String model = "local-model";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}

