package com.example.core.tools;

import com.example.core.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface Tool {

    String name();

    String description();

    Map<String, Object> parameters();

    default boolean allowedFor(CurrentUser user) {
        return true;
    }

    Object execute(CurrentUser user, JsonNode arguments);
}
