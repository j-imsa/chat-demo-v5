package com.example.core.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.core.auth.CurrentUser;
import com.example.core.grpc.v1.ToolCall;
import com.example.core.grpc.v1.ToolDefinition;
import com.example.core.grpc.v1.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static final CurrentUser USER = new CurrentUser("u1", "User", Set.of("user"));
    private static final CurrentUser ADMIN = new CurrentUser("a1", "Admin", Set.of("user", "admin"));

    private final ToolRegistry registry = new ToolRegistry(
            List.of(new CurrentTimeTool(), new UserProfileTool(), new ServerStatusTool()), new ObjectMapper());

    @Test
    void catalogIsFilteredByRole() {
        assertThat(registry.catalogFor(USER)).extracting(ToolDefinition::getName)
                .containsExactly("get_current_time", "get_user_profile");
        assertThat(registry.catalogFor(ADMIN)).extracting(ToolDefinition::getName)
                .containsExactly("get_current_time", "get_server_status", "get_user_profile");
    }

    @Test
    void executionIsAuthorizedAgain() {
        ToolResult result = registry.execute(USER, "r1", call("get_server_status", "{}"));
        assertThat(result.getOk()).isFalse();
        assertThat(result.getError()).startsWith("Not authorized");
        assertThat(registry.execute(ADMIN, "r2", call("get_server_status", "{}")).getOk()).isTrue();
    }

    @Test
    void unknownToolAndBadArgumentsAreNormalFailures() {
        assertThat(registry.execute(USER, "r1", call("delete_everything", "{}")).getError()).startsWith("Unknown tool");
        assertThat(registry.execute(USER, "r2", call("get_current_time", "{\"timezone\":\"Mars/Base\"}")).getError())
                .startsWith("Unknown timezone");
        assertThat(registry.execute(USER, "r3", call("get_current_time", "not json")).getError())
                .startsWith("Invalid tool arguments");
    }

    @Test
    void successfulCallReturnsJsonForTheRequestingUser() {
        ToolResult result = registry.execute(USER, "r1", call("get_user_profile", ""));
        assertThat(result.getOk()).isTrue();
        assertThat(result.getRequestId()).isEqualTo("r1");
        assertThat(result.getCallId()).isEqualTo("c-get_user_profile");
        assertThat(result.getContent()).contains("\"id\":\"u1\"");
    }

    private static ToolCall call(String name, String arguments) {
        return ToolCall.newBuilder().setId("c-" + name).setName(name).setArgumentsJson(arguments).build();
    }
}
