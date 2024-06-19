package io.archer.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Promotion {
    public static Promotion valueOf(Map<String, String> map) {
        if (map == null) {
            return null;
        } else {
            Promotion promotion = new Promotion()
                    .setId(UUID.fromString(map.get("id")))
                    .setUser(map.get("user"))
                    .setActivity(map.get("activity"))
                    .setCode(map.get("code"));
            if (Objects.nonNull(map.get("issuedTime")))
                promotion.setIssuedTime(Instant.parse(map.get("issuedTime")));
            if (Objects.nonNull(map.get("reservedTime")))
                promotion.setReservedTime(Instant.parse(map.get("reservedTime")));
            return promotion;
        }
    }

    private UUID id;
    private String user;
    private String activity;
    private String code;
    private Instant issuedTime;
    private Instant reservedTime;

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (id != null) {
            map.put("id", id.toString());
        }
        if (user != null) {
            map.put("user", user);
        }
        if (activity != null) {
            map.put("activity", activity);
        }
        if (code != null) {
            map.put("code", code);
        }
        if (issuedTime != null) {
            map.put("issuedTime", DateTimeFormatter.ISO_INSTANT.format(issuedTime));
        }
        if (reservedTime != null) {
            map.put("reservedTime", DateTimeFormatter.ISO_INSTANT.format(reservedTime));
        }
        return map;
    }
}
