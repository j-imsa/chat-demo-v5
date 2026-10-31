package com.example.core.tools;

import com.example.core.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ServerStatusTool implements Tool {

    static final String ADMIN_ROLE = "admin";

    @Override
    public String name() {
        return "get_server_status";
    }

    @Override
    public String description() {
        return "Returns uptime and memory usage of the core server. Administrators only.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public boolean allowedFor(CurrentUser user) {
        return user.hasRole(ADMIN_ROLE);
    }

    @Override
    public Object execute(CurrentUser user, JsonNode arguments) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return Map.of(
                "uptime", Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()).withNanos(0).toString(),
                "usedMemoryMb", usedMb,
                "maxMemoryMb", runtime.maxMemory() / (1024 * 1024));
    }
}
