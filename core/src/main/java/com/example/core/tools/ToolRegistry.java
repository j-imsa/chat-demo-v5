package com.example.core.tools;

import com.example.core.auth.CurrentUser;
import com.example.core.grpc.v1.ToolCall;
import com.example.core.grpc.v1.ToolDefinition;
import com.example.core.grpc.v1.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> tools;
    private final ObjectMapper mapper;

    ToolRegistry(List<Tool> tools, ObjectMapper mapper) {
        this.tools = tools.stream().collect(Collectors.toMap(Tool::name, Function.identity()));
        this.mapper = mapper;
    }

    public List<ToolDefinition> catalogFor(CurrentUser user) {
        return tools.values().stream()
                .filter(tool -> tool.allowedFor(user))
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .map(this::definition)
                .toList();
    }

    public ToolResult execute(CurrentUser user, String requestId, ToolCall call) {
        ToolResult.Builder result = ToolResult.newBuilder().setRequestId(requestId).setCallId(call.getId());
        Tool tool = tools.get(call.getName());
        if (tool == null) {
            log.warn("user {} requested unknown tool {}", user.id(), call.getName());
            return result.setOk(false).setError("Unknown tool: " + call.getName()).build();
        }
        if (!tool.allowedFor(user)) {
            log.warn("user {} is not authorized for tool {}", user.id(), call.getName());
            return result.setOk(false).setError("Not authorized to use tool: " + call.getName()).build();
        }
        try {
            JsonNode arguments = mapper.readTree(call.getArgumentsJson().isBlank() ? "{}" : call.getArgumentsJson());
            Object output = tool.execute(user, arguments);
            log.info("user {} executed tool {}", user.id(), call.getName());
            return result.setOk(true).setContent(mapper.writeValueAsString(output)).build();
        } catch (JsonProcessingException e) {
            return result.setOk(false).setError("Invalid tool arguments: " + e.getOriginalMessage()).build();
        } catch (RuntimeException e) {
            log.warn("tool {} failed for user {}: {}", call.getName(), user.id(), e.getMessage());
            return result.setOk(false).setError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()).build();
        }
    }

    private ToolDefinition definition(Tool tool) {
        try {
            return ToolDefinition.newBuilder()
                    .setName(tool.name())
                    .setDescription(tool.description())
                    .setParametersJsonSchema(mapper.writeValueAsString(tool.parameters()))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid schema for tool " + tool.name(), e);
        }
    }
}
