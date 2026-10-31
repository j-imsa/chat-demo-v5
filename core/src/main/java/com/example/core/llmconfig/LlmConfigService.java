package com.example.core.llmconfig;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmConfigService {

    public static final String PROVIDER_OLLAMA = "ollama";

    private final LlmConfigRepository repository;

    LlmConfigService(LlmConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<LlmConfig> current() {
        return repository.findById(LlmConfig.SINGLETON_ID);
    }

    @Transactional
    public LlmConfig save(String baseUrl, String model) {
        String normalizedUrl = baseUrl.strip().replaceAll("/+$", "");
        String normalizedModel = model.strip();
        LlmConfig config = repository.findById(LlmConfig.SINGLETON_ID)
                .map(existing -> {
                    existing.update(PROVIDER_OLLAMA, normalizedUrl, normalizedModel);
                    return existing;
                })
                .orElseGet(() -> new LlmConfig(PROVIDER_OLLAMA, normalizedUrl, normalizedModel));
        return repository.save(config);
    }

    @Transactional
    public void clear() {
        repository.deleteById(LlmConfig.SINGLETON_ID);
    }
}
