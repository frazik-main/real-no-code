package com.realnocode.service;

import com.realnocode.config.AiProviderProperties;
import com.realnocode.model.PageHistory;
import com.realnocode.repository.PageHistoryRepository;
import com.realnocode.service.provider.AiProviderClient;
import com.realnocode.service.provider.AiProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotServiceTest {

    @Test
    void usesRequestedProviderWhenPresent() {
        PageHistoryRepository repository = mock(PageHistoryRepository.class);
        when(repository.save(any(PageHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProviderProperties properties = new AiProviderProperties();
        properties.setDefaultProvider("copilot");

        CopilotService service = new CopilotService(
                repository,
                properties,
                List.of(
                        stub(AiProviderType.COPILOT, "<!DOCTYPE html><html><body>copilot</body></html>"),
                        stub(AiProviderType.GEMINI, "<!DOCTYPE html><html><body>gemini</body></html>"),
                        stub(AiProviderType.LOCAL, "<!DOCTYPE html><html><body>local</body></html>")
                )
        );

        String result = service.generatePage("landing page", Map.of(), "gemini");

        assertTrue(result.contains("gemini"));
        assertTrue(result.contains("encodeURIComponent('gemini')"));
    }

    @Test
    void fallsBackToDefaultProviderWhenUnknownRequested() {
        PageHistoryRepository repository = mock(PageHistoryRepository.class);
        when(repository.save(any(PageHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProviderProperties properties = new AiProviderProperties();
        properties.setDefaultProvider("local");

        CopilotService service = new CopilotService(
                repository,
                properties,
                List.of(stub(AiProviderType.LOCAL, "<!DOCTYPE html><html><body>local</body></html>"))
        );

        String result = service.generatePage("landing page", Map.of(), "unknown-provider");

        assertTrue(result.contains("local"));
        assertTrue(result.contains("encodeURIComponent('local')"));
    }

    @Test
    void businessIdeaPageInjectsReplaceStateWithContextPath() {
        PageHistoryRepository repository = mock(PageHistoryRepository.class);
        when(repository.save(any(PageHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProviderProperties properties = new AiProviderProperties();
        properties.setDefaultProvider("gemini");

        CopilotService service = new CopilotService(
                repository,
                properties,
                List.of(stub(AiProviderType.GEMINI, "<!DOCTYPE html><html><body>idea</body></html>"))
        );

        String result = service.generateBusinessIdeaPage("SaaS for dentists", Map.of(), "gemini", "/business/saas-for-dentists");

        // The path is injected into the initialContextPath variable, and replaceState uses that variable
        assertTrue(result.contains("initialContextPath = '/business/saas-for-dentists'"),
                "Expected context path injected into initialContextPath variable:\n" + extractScript(result));
        assertTrue(result.contains("history.replaceState({}, '', initialContextPath)"),
                "Expected replaceState call using initialContextPath variable:\n" + extractScript(result));
        assertTrue(result.contains("contextEnabled = 'true' === 'true'"),
                "Expected contextEnabled=true for business pages:\n" + extractScript(result));
    }

    @Test
    void regularPageHasNoReplaceStateAndContextDisabled() {
        PageHistoryRepository repository = mock(PageHistoryRepository.class);
        when(repository.save(any(PageHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProviderProperties properties = new AiProviderProperties();
        properties.setDefaultProvider("gemini");

        CopilotService service = new CopilotService(
                repository,
                properties,
                List.of(stub(AiProviderType.GEMINI, "<!DOCTYPE html><html><body>page</body></html>"))
        );

        String result = service.generatePage("landing page", Map.of(), "gemini");

        assertTrue(result.contains("contextEnabled = 'false' === 'true'"),
                "Expected contextEnabled=false for regular pages:\n" + extractScript(result));
    }

    @Test
    void subsequentClickBuildsPathFromCurrentContext() {
        PageHistoryRepository repository = mock(PageHistoryRepository.class);
        when(repository.save(any(PageHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProviderProperties properties = new AiProviderProperties();
        properties.setDefaultProvider("gemini");

        CopilotService service = new CopilotService(
                repository,
                properties,
                List.of(stub(AiProviderType.GEMINI, "<!DOCTYPE html><html><body>idea</body></html>"))
        );

        String result = service.generateBusinessIdeaPage("dentist SaaS", Map.of(), "gemini", "/business/dentist-saas/revenue-model");

        assertTrue(result.contains("initialContextPath = '/business/dentist-saas/revenue-model'"),
                "Expected nested context path injected:\n" + extractScript(result));
        assertTrue(result.contains("fetch('/business/generate'"),
                "Expected business generate endpoint:\n" + extractScript(result));
    }

    private String extractScript(String html) {
        int start = html.lastIndexOf("<script>");
        return start >= 0 ? html.substring(start, Math.min(start + 600, html.length())) : "(no script found)";
    }

    private AiProviderClient stub(AiProviderType type, String html) {
        return new AiProviderClient() {
            @Override
            public AiProviderType type() {
                return type;
            }

            @Override
            public String generateHtml(String prompt, String userPrompt, String generateEndpoint) {
                return html;
            }
        };
    }
}


