package com.example.core.llm;

import com.example.core.grpc.v1.LlmMessage;
import com.example.core.grpc.v1.Role;
import com.example.core.grpc.v1.ToolCall;
import com.example.core.grpc.v1.ToolDefinition;
import com.example.core.llmconfig.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public List<ToolCall> streamChat(LlmConfig config, List<LlmMessage> messages, List<ToolDefinition> tools,
                                     float temperature, Consumer<String> onToken, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("messages", messages.stream().map(this::message).toList());
        body.put("options", Map.of("temperature", temperature));
        if (!tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::tool).toList());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        List<ToolCall> toolCalls = new ArrayList<>();
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
                JsonNode message = node.path("message");
                String content = message.path("content").asText("");
                if (!content.isEmpty()) {
                    onToken.accept(content);
                }
                for (JsonNode call : message.path("tool_calls")) {
                    toolCalls.add(toolCall(call));
                }
                if (node.path("done").asBoolean(false)) {
                    break;
                }
            }
        }
        return toolCalls;
    }

    private Map<String, Object> message(LlmMessage message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", role(message.getRole()));
        out.put("content", message.getContent());
        if (message.getToolCallsCount() > 0) {
            out.put("tool_calls", message.getToolCallsList().stream()
                    .map(call -> Map.of("function", Map.of("name", call.getName(), "arguments", json(call.getArgumentsJson()))))
                    .toList());
        }
        if (message.getRole() == Role.TOOL) {
            out.put("tool_name", message.getToolName());
        }
        return out;
    }

    private Map<String, Object> tool(ToolDefinition tool) {
        return Map.of("type", "function", "function", Map.of(
                "name", tool.getName(),
                "description", tool.getDescription(),
                "parameters", json(tool.getParametersJsonSchema())));
    }

    private ToolCall toolCall(JsonNode call) throws IOException {
        JsonNode function = call.path("function");
        String id = call.path("id").asText("");
        return ToolCall.newBuilder()
                .setId(id.isEmpty() ? "call_" + UUID.randomUUID().toString().substring(0, 8) : id)
                .setName(function.path("name").asText())
                .setArgumentsJson(mapper.writeValueAsString(function.path("arguments")))
                .build();
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value.isBlank() ? "{}" : value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String role(Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            default -> "user";
        };
    }
}
