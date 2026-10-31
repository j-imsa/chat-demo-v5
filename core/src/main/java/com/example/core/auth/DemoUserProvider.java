package com.example.core.auth;

import org.springframework.stereotype.Component;

@Component
public class DemoUserProvider implements CurrentUserProvider {

    private static final CurrentUser DEMO_USER = new CurrentUser("demo-user");

    @Override
    public CurrentUser currentUser() {
        return DEMO_USER;
    }
}
