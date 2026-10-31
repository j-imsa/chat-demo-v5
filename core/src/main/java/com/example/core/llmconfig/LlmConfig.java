package com.example.core.llmconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;

@Entity
public class LlmConfig {

    static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Instant updatedAt;

    protected LlmConfig() {
    }

    LlmConfig(String provider, String baseUrl, String model) {
        update(provider, baseUrl, model);
    }

    void update(String provider, String baseUrl, String model) {
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.updatedAt = Instant.now();
    }

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
