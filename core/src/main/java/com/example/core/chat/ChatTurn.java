package com.example.core.chat;

import com.example.core.auth.CurrentUser;
import com.example.core.grpc.v1.AiServiceGrpc;
import com.example.core.grpc.v1.AiToCore;
import com.example.core.grpc.v1.ChatCompleted;
import com.example.core.grpc.v1.ChatRequest;
import com.example.core.grpc.v1.ChatToolActivity;
import com.example.core.grpc.v1.CoreToAi;
import com.example.core.grpc.v1.LlmAvailability;
import com.example.core.grpc.v1.LlmCallRequest;
import com.example.core.grpc.v1.LlmChunk;
import com.example.core.grpc.v1.LlmCompletion;
import com.example.core.grpc.v1.LlmInfo;
import com.example.core.grpc.v1.ToolCall;
import com.example.core.grpc.v1.ToolCallRequest;
import com.example.core.grpc.v1.ToolCatalog;
import com.example.core.llm.OllamaClient;
import com.example.core.llmconfig.LlmConfig;
import com.example.core.llmconfig.LlmConfigService;
import com.example.core.tools.ToolRegistry;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class ChatTurn implements StreamObserver<AiToCore> {

    private static final Logger log = LoggerFactory.getLogger(ChatTurn.class);
    private static final String NOT_CONFIGURED = "No LLM is configured in core";

    private final SseEmitter emitter;
    private final CurrentUser user;
    private final LlmConfigService llmConfigs;
    private final OllamaClient ollama;
    private final ToolRegistry tools;
    private final ExecutorService llmExecutor;
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile boolean cancelled;
    private ClientCallStreamObserver<CoreToAi> outbound;

    ChatTurn(SseEmitter emitter, CurrentUser user, LlmConfigService llmConfigs, OllamaClient ollama,
             ToolRegistry tools, ExecutorService llmExecutor) {
        this.emitter = emitter;
        this.user = user;
        this.llmConfigs = llmConfigs;
        this.ollama = ollama;
        this.tools = tools;
        this.llmExecutor = llmExecutor;
    }

    void start(AiServiceGrpc.AiServiceStub aiService, ChatRequest request) {
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(this::cancel);
        emitter.onError(error -> cancel());
        outbound = (ClientCallStreamObserver<CoreToAi>) aiService.chat(this);
        send(CoreToAi.newBuilder().setChatRequest(request).build());
    }

    @Override
    public void onNext(AiToCore message) {
        switch (message.getPayloadCase()) {
            case LLM_AVAILABILITY_QUERY -> answerAvailability(message.getLlmAvailabilityQuery().getRequestId());
            case LLM_CALL_REQUEST -> llmExecutor.submit(() -> runLlmCall(message.getLlmCallRequest()));
            case TOOL_CATALOG_QUERY -> answerToolCatalog(message.getToolCatalogQuery().getRequestId());
            case TOOL_CALL_REQUEST -> llmExecutor.submit(() -> runToolCall(message.getToolCallRequest()));
            case CHAT_DELTA -> emit("delta", Map.of("content", message.getChatDelta().getContent()));
            case CHAT_TOOL_ACTIVITY -> emitToolActivity(message.getChatToolActivity());
            case CHAT_COMPLETED -> complete(message.getChatCompleted());
            case PAYLOAD_NOT_SET -> log.warn("ai-service sent an empty message");
        }
    }

    @Override
    public void onError(Throwable error) {
        cancelled = true;
        if (finished.compareAndSet(false, true)) {
            log.warn("ai-service stream failed", error);
            emit("error", Map.of("message", "The AI service is unavailable: " + Status.fromThrowable(error).getCode()));
            emitter.complete();
        }
    }

    @Override
    public void onCompleted() {
        cancelled = true;
        if (finished.compareAndSet(false, true)) {
            emit("error", Map.of("message", "The AI service ended the conversation turn unexpectedly"));
            emitter.complete();
        }
    }

    private void answerAvailability(String requestId) {
        Optional<LlmConfig> config = llmConfigs.current();
        LlmAvailability.Builder availability = LlmAvailability.newBuilder().setRequestId(requestId);
        config.ifPresentOrElse(
                c -> availability.setAvailable(true)
                        .setInfo(LlmInfo.newBuilder().setProvider(c.getProvider()).setModel(c.getModel())),
                () -> availability.setAvailable(false).setReason(NOT_CONFIGURED));
        send(CoreToAi.newBuilder().setLlmAvailability(availability).build());
    }

    private void answerToolCatalog(String requestId) {
        send(CoreToAi.newBuilder()
                .setToolCatalog(ToolCatalog.newBuilder().setRequestId(requestId).addAllTools(tools.catalogFor(user)))
                .build());
    }

    private void runToolCall(ToolCallRequest request) {
        if (!cancelled) {
            send(CoreToAi.newBuilder().setToolResult(tools.execute(user, request.getRequestId(), request.getCall())).build());
        }
    }

    private void emitToolActivity(ChatToolActivity activity) {
        emit("tool", Map.of("callId", activity.getCallId(), "name", activity.getName(), "phase", activity.getPhase().name()));
    }

    private void runLlmCall(LlmCallRequest call) {
        LlmCompletion.Builder completion = LlmCompletion.newBuilder().setRequestId(call.getRequestId());
        try {
            LlmConfig config = llmConfigs.current().orElseThrow(() -> new IllegalStateException(NOT_CONFIGURED));
            List<ToolCall> toolCalls = ollama.streamChat(config, call.getMessagesList(), call.getToolsList(),
                    call.getTemperature(),
                    token -> send(CoreToAi.newBuilder()
                            .setLlmChunk(LlmChunk.newBuilder().setRequestId(call.getRequestId()).setContent(token))
                            .build()),
                    () -> cancelled);
            completion.setOk(true).addAllToolCalls(toolCalls);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completion.setOk(false).setError("interrupted");
        } catch (Exception e) {
            log.warn("LLM call failed", e);
            completion.setOk(false).setError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        if (!cancelled) {
            send(CoreToAi.newBuilder().setLlmCompletion(completion).build());
        }
    }

    private void complete(ChatCompleted completed) {
        if (finished.compareAndSet(false, true)) {
            emit("done", Map.of("status", completed.getStatus().name(), "message", completed.getMessage()));
            synchronized (this) {
                outbound.onCompleted();
            }
            emitter.complete();
        }
    }

    private void emit(String event, Map<String, String> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.info("ui disconnected: {}", e.getMessage());
            cancel();
        }
    }

    private synchronized void send(CoreToAi message) {
        if (!cancelled) {
            outbound.onNext(message);
        }
    }

    private void cancel() {
        if (finished.compareAndSet(false, true)) {
            cancelled = true;
            synchronized (this) {
                outbound.cancel("ui disconnected", null);
            }
        }
    }
}
