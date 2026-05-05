package com.realnocode.controller;

import com.realnocode.service.CopilotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class BusinessController {

    private final CopilotService copilotService;

    public BusinessController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @GetMapping({"/business", "/business/**"})
    public String index(Model model) {
        model.addAttribute("defaultProvider", copilotService.getDefaultProvider());
        return "index";
    }

    @PostMapping(value = "/business/generate", produces = "text/html; charset=UTF-8")
    @ResponseBody
    public String generate(@RequestParam Map<String, String> allParams) {
        String prompt = allParams.getOrDefault("prompt", "").trim();
        if (prompt.isBlank()) {
            prompt = "Generate a business idea with customer problem, target audience, monetization, go-to-market, and first 90-day action plan.";
        }

        String provider = allParams.getOrDefault("provider", "").trim();
        String contextPath = normalizeContextPath(allParams.getOrDefault("contextPath", "/business"));
        Map<String, String> formData = new HashMap<>(allParams);
        formData.remove("prompt");
        formData.remove("provider");
        formData.remove("contextPath");
        return copilotService.generateBusinessIdeaPage(prompt, formData, provider, contextPath);
    }

    private String normalizeContextPath(String rawContextPath) {
        if (rawContextPath == null || rawContextPath.isBlank()) {
            return "/business";
        }
        String normalized = rawContextPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!normalized.startsWith("/business")) {
            return "/business";
        }
        return normalized.replaceAll("/+", "/").replaceAll("/$", "");
    }
}
