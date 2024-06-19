package io.archer.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.sql.Timestamp;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
public class Activity {
    private UUID id;
    private Timestamp startTime;
    private String duration;
    private Integer amount;
}
