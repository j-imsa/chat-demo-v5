package com.example.core.tools;

import com.example.core.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
class UserProfileTool implements Tool {

    @Override
    public String name() {
        return "get_user_profile";
    }

    @Override
    public String description() {
        return "Returns the profile of the user who is chatting: id, display name and roles.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(CurrentUser user, JsonNode arguments) {
        return Map.of(
                "id", user.id(),
                "displayName", user.displayName(),
                "roles", new TreeSet<>(user.roles()));
    }
}
