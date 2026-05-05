package com.realnocode.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "page_history")
public class PageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String prompt;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String generatedHtml;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PageHistory() {
    }

    public PageHistory(String prompt, String generatedHtml) {
        this.prompt = prompt;
        this.generatedHtml = generatedHtml;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getGeneratedHtml() {
        return generatedHtml;
    }

    public void setGeneratedHtml(String generatedHtml) {
        this.generatedHtml = generatedHtml;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
