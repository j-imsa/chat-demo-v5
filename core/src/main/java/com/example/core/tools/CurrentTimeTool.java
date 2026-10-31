package com.example.core.tools;

import com.example.core.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class CurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "Returns the current date and time in an IANA timezone such as Asia/Tehran or Europe/Berlin.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "timezone", Map.of("type", "string", "description", "IANA timezone name, defaults to UTC")),
                "required", new String[0]);
    }

    @Override
    public Object execute(CurrentUser user, JsonNode arguments) {
        String timezone = arguments.path("timezone").asText("UTC");
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone.isBlank() ? "UTC" : timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
        ZonedDateTime now = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS);
        return Map.of(
                "timezone", zone.getId(),
                "dateTime", now.toOffsetDateTime().toString(),
                "dayOfWeek", now.getDayOfWeek().toString());
    }
}
