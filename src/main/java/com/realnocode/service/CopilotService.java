package com.realnocode.service;

import com.realnocode.config.AiProviderProperties;
import com.realnocode.model.PageHistory;
import com.realnocode.repository.PageHistoryRepository;
import com.realnocode.service.provider.AiProviderClient;
import com.realnocode.service.provider.AiProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    // Injected into every generated page — placeholders are replaced at runtime.
    private static final String INTERACTIVE_SCRIPT_TEMPLATE = """
            <style>
            .rn-hover { outline: 2px solid rgba(3,102,214,0.4) !important; border-radius: 4px !important; cursor: pointer !important; }
            </style>
            <script>
            (function() {
              var SEL = 'h1,h2,h3,h4,h5,h6,p,li,dt,dd,td,th,button,figcaption,blockquote,label,[class*="card"],[class*="item"],[class*="tag"],[class*="badge"],[class*="chip"],[class*="tile"]';
              var BUSINESS_ROOT = '/business';
              var initialContextPath = '__CONTEXT_PATH__';
              var contextEnabled = '__CONTEXT_ENABLED__' === 'true';

              if (contextEnabled && initialContextPath) {
                history.replaceState({}, '', initialContextPath);
              }

              var pageTitle = (document.querySelector('h1') || {}).textContent || document.title || '';

              function nearestHeading(el) {
                var node = el;
                while (node && node.tagName !== 'BODY') {
                  var s = node.previousElementSibling;
                  while (s) { if (/^H[1-6]$/.test(s.tagName)) return s.textContent.trim(); s = s.previousElementSibling; }
                  node = node.parentElement;
                }
                return '';
              }

              function makePrompt(el) {
                var text = el.textContent.trim().replace(/\\s+/g,' ').replace(/"/g,"'").substring(0,200);
                if (!text || text.length < 2) return null;
                var tag = el.tagName.toLowerCase();
                var heading = nearestHeading(el);
                if (/^h[1-6]$/.test(tag)) return 'Deep dive into: ' + text;
                if (tag === 'button' || tag === 'label') return 'Tell me about: ' + text;
                if (heading && heading !== text) return 'More about ' + text + ' — section: ' + heading + ' — page: ' + pageTitle;
                return 'Tell me more about: ' + text + ' — page context: ' + pageTitle;
              }

              function slugify(value) {
                var s = (value || '').toLowerCase().replace(/[^a-z0-9\\s-]/g, ' ').replace(/\\s+/g, '-').replace(/-+/g, '-').replace(/^-+|-+$/g, '');
                return s.substring(0, 40);
              }

              function currentBusinessPath() {
                var cur = (window.location.pathname || '').replace(/\\/+$/, '');
                if (cur.startsWith(BUSINESS_ROOT) && cur.length > BUSINESS_ROOT.length && cur !== '/business/generate') return cur;
                var fb = (initialContextPath || '').replace(/\\/+$/, '');
                if (fb.startsWith(BUSINESS_ROOT) && fb.length > BUSINESS_ROOT.length) return fb;
                return BUSINESS_ROOT;
              }

              function go(prompt, clickedText) {
                document.body.innerHTML = '<div style="font-family:sans-serif;text-align:center;padding:4rem;font-size:1.3rem;color:#586069;">⏳ Generating…</div>';
                var body = 'prompt=' + encodeURIComponent(prompt);
                if ('__PROVIDER__') {
                  body += '&provider=' + encodeURIComponent('__PROVIDER__');
                }
                if (contextEnabled) {
                  var slug = slugify(clickedText || prompt) || 'step';
                  var nextPath = currentBusinessPath() + '/' + slug;
                  body += '&contextPath=' + encodeURIComponent(nextPath);
                }
                fetch('__ENDPOINT__',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:body})
                  .then(function(r){return r.text();}).then(function(html){document.open();document.write(html);document.close();});
              }

              document.addEventListener('mouseover', function(e) {
                var el = e.target.closest(SEL + ',a'); if (el) el.classList.add('rn-hover');
              });
              document.addEventListener('mouseout', function(e) {
                var el = e.target.closest(SEL + ',a'); if (el) el.classList.remove('rn-hover');
              });
              document.addEventListener('click', function(e) {
                var el = e.target.closest(SEL + ',a');
                if (!el) return;
                if (el.tagName === 'A') {
                  var href = (el.getAttribute('href') || '').toLowerCase();
                  if (href.startsWith('javascript:')) return;
                }
                var prompt = makePrompt(el);
                if (!prompt) return;
                e.preventDefault(); e.stopPropagation();
                go(prompt, el.textContent || prompt);
              }, true);

              var t = document.createElement('div');
              t.textContent = '✨ Click anything to explore';
              t.style.cssText = 'position:fixed;bottom:1.5rem;right:1.5rem;background:#24292e;color:#fff;padding:0.6rem 1.2rem;border-radius:20px;font-family:sans-serif;font-size:0.85rem;opacity:1;transition:opacity 1s;z-index:9999;pointer-events:none;box-shadow:0 2px 8px rgba(0,0,0,0.3);';
              document.body.appendChild(t);
              setTimeout(function(){ t.style.opacity='0'; }, 3000);
              setTimeout(function(){ t.remove(); }, 4100);
            })();
            </script>
            """;

    private final PageHistoryRepository pageHistoryRepository;
    private final AiProviderProperties providerProperties;
    private final Map<AiProviderType, AiProviderClient> providerClients;

    public CopilotService(PageHistoryRepository pageHistoryRepository,
                          AiProviderProperties providerProperties,
                          List<AiProviderClient> clients) {
        this.pageHistoryRepository = pageHistoryRepository;
        this.providerProperties = providerProperties;
        this.providerClients = clients.stream()
                .collect(Collectors.toUnmodifiableMap(AiProviderClient::type, c -> c));
    }

    public String getDefaultProvider() {
        return resolveDefaultProvider().value();
    }

    public String generatePage(String userPrompt, Map<String, String> formData, String provider) {
        String fullPrompt = buildPrompt(userPrompt, formData);
        return generateWithProvider(fullPrompt, "/generate", userPrompt, provider, userPrompt, null);
    }


    public String generateBusinessIdeaPage(String userPrompt,
                                           Map<String, String> formData,
                                           String provider,
                                           String contextPath) {
        String fullPrompt = buildBusinessIdeaPrompt(userPrompt, formData, contextPath);
        return generateWithProvider(fullPrompt,
                "/business/generate",
                userPrompt,
                provider,
                "[business-idea] " + (contextPath == null ? "/business" : contextPath) + " :: " + userPrompt,
                contextPath);
    }

    private String generateWithProvider(String fullPrompt,
                                        String endpoint,
                                        String userPrompt,
                                        String requestedProvider,
                                        String historyPrompt,
                                        String contextPath) {
        AiProviderType providerType = resolveProvider(requestedProvider);
        AiProviderClient client = providerClients.get(providerType);
        if (client == null) {
            return buildFallbackPage("Provider '" + providerType.value() + "' is not configured.");
        }

        try {
            log.info("Calling provider '{}' with prompt length {}", providerType.value(), fullPrompt.length());
            String generatedHtml = client.generateHtml(fullPrompt, userPrompt, endpoint);
            String finalHtml = injectInteractiveScript(generatedHtml, endpoint, providerType.value(), contextPath);
            pageHistoryRepository.save(new PageHistory("[" + providerType.value() + "] " + historyPrompt, finalHtml));
            return finalHtml;
        } catch (TimeoutException e) {
            return buildFallbackPage("Provider '" + providerType.value() + "' timed out after "
                    + providerProperties.getTimeoutSeconds() + "s.");
        } catch (Exception e) {
            log.error("Error calling provider '{}'", providerType.value(), e);
            return buildFallbackPage("Provider '" + providerType.value() + "' failed: " + safeError(e.getMessage()));
        }
    }

    public List<PageHistory> getHistory() {
        return pageHistoryRepository.findAllByOrderByCreatedAtDesc();
    }

    private AiProviderType resolveDefaultProvider() {
        return AiProviderType.fromValue(providerProperties.getDefaultProvider(), AiProviderType.COPILOT);
    }

    private AiProviderType resolveProvider(String requestedProvider) {
        AiProviderType defaultProvider = resolveDefaultProvider();
        AiProviderType selected = AiProviderType.fromValue(requestedProvider, defaultProvider);
        if (!providerClients.containsKey(selected) && providerClients.containsKey(defaultProvider)) {
            return defaultProvider;
        }
        return selected;
    }

    private String buildPrompt(String userPrompt, Map<String, String> formData) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a BRAND NEW complete, self-contained HTML page entirely from scratch. ");
        sb.append("Do NOT read, reference, or return any existing files from the filesystem. ");
        sb.append("Do not create any files. Output only the new HTML page, nothing else. ");
        sb.append("Requirements: ").append(userPrompt);
        if (formData != null && !formData.isEmpty()) {
            sb.append("\n\nExtra parameters:\n");
            formData.forEach((key, value) ->
                    sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
        }
        return sb.toString();
    }

    private String buildBusinessIdeaPrompt(String userPrompt, Map<String, String> formData, String contextPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a BRAND NEW complete, self-contained HTML page for a business idea. ");
        sb.append("Do NOT read, reference, or return any existing files from the filesystem. ");
        sb.append("Do not create any files. Output only the new HTML page, nothing else.\n\n");
        sb.append("Request: ").append(userPrompt).append("\n\n");
        if (contextPath != null && !contextPath.isBlank()) {
            sb.append("Business idea context path: ").append(contextPath).append("\n");
            sb.append("Treat each new page as a deeper navigation step under that context.\n\n");
        }
        sb.append("""
                Idea page requirements:
                - Focus on one concrete business idea, not a generic app shell
                - Include these sections: Problem, Solution, Target Customers, Revenue Model, Go-To-Market, Risks, Next 90 Days
                - Add realistic assumptions and small numeric examples (pricing, conversion, basic monthly projection)
                - Use clear headings and concise bullets suitable for founders/investors
                - Do NOT create scrum boards, kanban boards, sprint dashboards, or generic KPI dashboards unless explicitly requested
                - Keep all content directly tied to the request text
                """);
        if (formData != null && !formData.isEmpty()) {
            sb.append("\nExtra parameters:\n");
            formData.forEach((key, value) ->
                    sb.append("- ").append(key).append(": ").append(value).append("\n")
            );
        }
        return sb.toString();
    }

    private String injectInteractiveScript(String output, String endpoint, String provider, String contextPath) {
        String content = output == null ? "" : output.replaceFirst("^\\s*[\\u25CF\\u2022]\\s*", "").trim();

        if (content.isBlank()) {
            return buildFallbackPage("Provider returned an empty response.");
        }

        if (content.startsWith("```")) {
            int firstNewline = content.indexOf('\n');
            int lastFence = content.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                content = content.substring(firstNewline + 1, lastFence).trim();
            }
        }

        String script = INTERACTIVE_SCRIPT_TEMPLATE
                .replace("__ENDPOINT__", endpoint)
                .replace("__PROVIDER__", provider == null ? "" : provider)
                .replace("__CONTEXT_PATH__", safeJsString(contextPath == null ? "" : contextPath))
                .replace("__CONTEXT_ENABLED__", contextPath == null || contextPath.isBlank() ? "false" : "true");

        int bodyClose = content.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            content = content.substring(0, bodyClose) + script + content.substring(bodyClose);
        } else {
            content = content + script;
        }

        return content;
    }

    private String safeJsString(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        String trimmed = message.length() > 350 ? message.substring(0, 350) + "..." : message;
        return trimmed.replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildFallbackPage(String errorMessage) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><title>Error</title>
                <style>body{font-family:sans-serif;max-width:600px;margin:2rem auto;padding:1rem;}
                .error{color:#c0392b;border:1px solid #e74c3c;padding:1rem;border-radius:4px;}</style>
                </head>
                <body>
                <h1>Real No Code</h1>
                <div class="error">
                  <strong>Could not generate page:</strong><br>%s
                </div>
                <p><a href="/">Back to home</a></p>
                </body>
                </html>
                """.formatted(errorMessage);
    }
}
