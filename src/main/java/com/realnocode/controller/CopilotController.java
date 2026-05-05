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
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("defaultProvider", copilotService.getDefaultProvider());
        return "index";
    }

    @PostMapping(value = "/generate", produces = "text/html; charset=UTF-8")
    @ResponseBody
    public String generate(@RequestParam Map<String, String> allParams) {
        String prompt = allParams.getOrDefault("prompt", "").trim();
        if (prompt.isBlank()) {
            prompt = "A simple welcome page";
        }

        String provider = allParams.getOrDefault("provider", "").trim();
        Map<String, String> formData = new HashMap<>(allParams);
        formData.remove("prompt");
        formData.remove("provider");
        return copilotService.generatePage(prompt, formData, provider);
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("history", copilotService.getHistory());
        return "history";
    }
}
