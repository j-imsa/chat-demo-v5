package com.example.core.llmconfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm-config")
class LlmConfigController {

    record LlmConfigRequest(
            @NotBlank @Pattern(regexp = "https?://\\S+", message = "must be an http(s) URL") String baseUrl,
            @NotBlank String model) {
    }

    record LlmConfigResponse(String provider, String baseUrl, String model, Instant updatedAt) {

        static LlmConfigResponse of(LlmConfig config) {
            return new LlmConfigResponse(config.getProvider(), config.getBaseUrl(), config.getModel(), config.getUpdatedAt());
        }
    }

    private final LlmConfigService service;

    LlmConfigController(LlmConfigService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<LlmConfigResponse> get() {
        return ResponseEntity.of(service.current().map(LlmConfigResponse::of));
    }

    @PutMapping
    LlmConfigResponse put(@Valid @RequestBody LlmConfigRequest request) {
        return LlmConfigResponse.of(service.save(request.baseUrl(), request.model()));
    }

    @DeleteMapping
    ResponseEntity<Void> delete() {
        service.clear();
        return ResponseEntity.noContent().build();
    }
}
