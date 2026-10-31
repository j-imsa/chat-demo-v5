package com.example.core.chat;

import java.io.IOException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice(assignableTypes = ChatController.class)
class ClientDisconnectHandler {

    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    void clientDisconnected() {
    }
}
