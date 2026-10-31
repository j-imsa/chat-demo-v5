package com.example.core.auth;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DemoUserProvider implements CurrentUserProvider {

    private static final CurrentUser DEMO_USER = new CurrentUser("demo-user", "Demo User", Set.of("user"));

    @Override
    public CurrentUser currentUser() {
        return DEMO_USER;
    }
}
