package com.example.core.llm;

import com.example.core.grpc.v1.LlmMessage;
import com.example.core.llmconfig.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class OllamaClient {

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper;

    OllamaClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void streamChat(LlmConfig config, List<LlmMessage> messages, float temperature,
                           Consumer<String> onToken, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "stream", true,
                "messages", messages.stream()
                        .map(m -> Map.of("role", role(m), "content", m.getContent()))
                        .toList(),
                "options", Map.of("temperature", temperature));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = response.body()) {
            if (response.statusCode() != 200) {
                throw new LlmException("Ollama returned HTTP " + response.statusCode() + ": "
                        + lines.collect(Collectors.joining(" ")));
            }
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext() && !cancelled.getAsBoolean()) {
                String line = iterator.next();
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = mapper.readTree(line);
                if (node.hasNonNull("error")) {
                    throw new LlmException(node.get("error").asText());
                }
                String content = node.path("message").path("content").asText("");
                if (!content.isEmpty()) {
                    onToken.accept(content);
                }
                if (node.path("done").asBoolean(false)) {
                    return;
                }
            }
        }
    }

    private static String role(LlmMessage message) {
        return switch (message.getRole()) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            default -> "user";
        };
    }
}
