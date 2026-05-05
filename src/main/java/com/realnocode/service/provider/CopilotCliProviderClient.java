package com.realnocode.service.provider;

import com.realnocode.config.AiProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class CopilotCliProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliProviderClient.class);

    private final AiProviderProperties properties;

    public CopilotCliProviderClient(AiProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public AiProviderType type() {
        return AiProviderType.COPILOT;
    }

    @Override
    public String generateHtml(String prompt, String userPrompt, String generateEndpoint) throws Exception {
        IOException lastStartError = null;
        for (String executable : resolveCopilotExecutables()) {
            try {
                Path genWorkspace = Path.of(System.getProperty("user.home"), ".copilot-gen");
                Files.createDirectories(genWorkspace);

                CopilotRunResult result = runCopilot(executable, prompt, genWorkspace);
                int exitCode = result.exitCode();
                String output = result.output();
                log.info("Copilot exited with code {}. Output length: {} chars", exitCode, output.length());

                if (exitCode != 0) {
                    throw new IllegalStateException("Copilot CLI exited with code " + exitCode + ". " + snippet(output));
                }

                if (isLauncherTemplateEcho(output)) {
                    String retryPrompt = strengthenPromptForRetry(prompt, generateEndpoint);
                    CopilotRunResult retry = runCopilot(executable, retryPrompt, genWorkspace);
                    if (retry.exitCode() == 0 && !isLauncherTemplateEcho(retry.output())) {
                        return retry.output();
                    }
                    throw new IllegalStateException("Copilot returned launcher template instead of generated HTML. "
                            + snippet(retry.output()));
                }

                if (isBusinessGenericMismatch(output, userPrompt, generateEndpoint)) {
                    String retryPrompt = strengthenPromptForRelevance(prompt, userPrompt);
                    CopilotRunResult retry = runCopilot(executable, retryPrompt, genWorkspace);
                    if (retry.exitCode() == 0) {
                        return retry.output();
                    }
                }

                return output;
            } catch (IOException startError) {
                lastStartError = startError;
                log.warn("Failed to start '{}': {}", executable, startError.getMessage());
            }
        }

        if (lastStartError != null) {
            throw new IllegalStateException("Copilot CLI not found. Set ai.copilot.command", lastStartError);
        }
        throw new IllegalStateException("Copilot CLI not available.");
    }

    private String snippet(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > 350 ? output.substring(0, 350) + "..." : output;
    }

    private CopilotRunResult runCopilot(String executable, String prompt, Path workDir)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String safePrompt = prompt.replace("\"", "'").replace("\r", " ");
        List<String> command = List.of(executable, "-p", safePrompt, "--silent",
                "--no-ask-user", "--no-custom-instructions", "--disable-builtin-mcps", "--deny-tool=write,shell");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        Map<String, String> env = pb.environment();
        env.putIfAbsent("HOME", System.getProperty("user.home"));
        env.putIfAbsent("USERPROFILE", System.getProperty("user.home"));
        env.computeIfAbsent("APPDATA", k -> System.getProperty("user.home") + "\\AppData\\Roaming");
        env.computeIfAbsent("LOCALAPPDATA", k -> System.getProperty("user.home") + "\\AppData\\Local");

        Process process = pb.start();
        String output = readProcessOutput(process);
        return new CopilotRunResult(process.exitValue(), output);
    }

    private String readProcessOutput(Process process)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Process exceeded " + properties.getTimeoutSeconds() + "s timeout");
        }

        return outputFuture.get(5, TimeUnit.SECONDS).trim();
    }

    private List<String> resolveCopilotExecutables() {
        String configuredCommand = properties.getCopilot().getCommand();
        List<String> candidates = new ArrayList<>();
        candidates.add(configuredCommand);

        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            if ("copilot".equalsIgnoreCase(configuredCommand)) {
                candidates.add("copilot.cmd");
            }
            Path fallback = Path.of("C:\\Program Files\\nodejs\\copilot.cmd");
            if (Files.exists(fallback)) {
                candidates.add(fallback.toString());
            }
        }

        return candidates.stream()
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .toList();
    }

    private boolean isLauncherTemplateEcho(String html) {
        String text = html == null ? "" : html.toLowerCase();
        return text.contains("<form id=\"generateform\"")
                || text.contains("<form id=\"bizform\"")
                || text.contains("real no code - github copilot web server")
                || text.contains("business app generator - real no code")
                || text.contains("quick starts")
                || text.contains("describe your business application");
    }

    private String strengthenPromptForRetry(String prompt, String endpoint) {
        String mode = "/business/generate".equals(endpoint) ? "business application" : "web page";
        return prompt + "\n\nCRITICAL RETRY RULES:\n"
                + "- You previously returned a launcher template from the repository. That is invalid.\n"
                + "- Return a NEW " + mode + " from scratch with different structure/content.\n"
                + "- Do not include these phrases: 'Real No Code', 'Build a business app', 'Quick starts', 'Describe your business application'.\n"
                + "- Output only HTML starting with <!DOCTYPE html>.";
    }

    private String strengthenPromptForRelevance(String prompt, String userPrompt) {
        return prompt + "\n\nRELEVANCE LOCK:\n"
                + "- Your previous output was too generic and did not follow the user request.\n"
                + "- Center the page on this exact request: " + userPrompt + "\n"
                + "- Include the exact request text in the page title or H1.\n"
                + "- Include at least 5 concrete sections directly related to that request.\n"
                + "- Do NOT output a generic dashboard shell unless the request explicitly asks for a dashboard.";
    }

    private boolean isBusinessGenericMismatch(String output, String userPrompt, String endpoint) {
        if (!"/business/generate".equals(endpoint) || userPrompt == null || userPrompt.isBlank()) {
            return false;
        }
        String out = output == null ? "" : output.toLowerCase();
        String prompt = userPrompt.toLowerCase();

        boolean looksGeneric = out.contains("dashboard") || out.contains("nexaflow") || out.contains("kpi") || out.contains("overview");
        if (!looksGeneric) {
            return false;
        }

        Set<String> keywords = extractKeywords(prompt);
        if (keywords.isEmpty()) {
            return false;
        }
        long matches = keywords.stream().filter(out::contains).count();
        return matches == 0;
    }

    private Set<String> extractKeywords(String text) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "and", "for", "with", "that", "this", "from", "into", "about", "your", "you",
                "app", "application", "page", "build", "create", "make", "simple", "business"
        ));
        return Arrays.stream(text.split("[^a-z0-9]+"))
                .map(String::trim)
                .filter(w -> w.length() >= 4)
                .filter(w -> !stopWords.contains(w))
                .limit(8)
                .collect(Collectors.toSet());
    }

    private record CopilotRunResult(int exitCode, String output) {}
}

