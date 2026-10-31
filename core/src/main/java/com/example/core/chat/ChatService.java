package com.example.core.chat;

import com.example.core.auth.CurrentUser;
import com.example.core.grpc.v1.AiServiceGrpc;
import com.example.core.grpc.v1.ChatRequest;
import com.example.core.llm.OllamaClient;
import com.example.core.llmconfig.LlmConfigService;
import com.example.core.tools.ToolRegistry;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
class ChatService {

    private final AiServiceGrpc.AiServiceStub aiService;
    private final LlmConfigService llmConfigs;
    private final OllamaClient ollama;
    private final ToolRegistry tools;
    private final ExecutorService llmExecutor;
    private final Duration sseTimeout;

    ChatService(AiServiceGrpc.AiServiceStub aiService, LlmConfigService llmConfigs, OllamaClient ollama,
                ToolRegistry tools, ExecutorService llmExecutor, @Value("${chat.sse-timeout}") Duration sseTimeout) {
        this.aiService = aiService;
        this.llmConfigs = llmConfigs;
        this.ollama = ollama;
        this.tools = tools;
        this.llmExecutor = llmExecutor;
        this.sseTimeout = sseTimeout;
    }

    SseEmitter startTurn(CurrentUser user, String conversationId, String message) {
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        ChatRequest request = ChatRequest.newBuilder()
                .setConversationId(conversationId)
                .setUserId(user.id())
                .setMessage(message)
                .build();
        new ChatTurn(emitter, user, llmConfigs, ollama, tools, llmExecutor).start(aiService, request);
        return emitter;
    }
}
