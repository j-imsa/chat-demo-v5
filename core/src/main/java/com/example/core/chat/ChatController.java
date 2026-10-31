package com.example.core.chat;

import com.example.core.auth.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
class ChatController {

    record ChatMessageRequest(@NotBlank String conversationId, @NotBlank String message) {
    }

    private final CurrentUserProvider currentUserProvider;
    private final ChatService chatService;

    ChatController(CurrentUserProvider currentUserProvider, ChatService chatService) {
        this.currentUserProvider = currentUserProvider;
        this.chatService = chatService;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@Valid @RequestBody ChatMessageRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return chatService.startTurn(currentUserProvider.currentUser(), request.conversationId(), request.message());
    }
}
