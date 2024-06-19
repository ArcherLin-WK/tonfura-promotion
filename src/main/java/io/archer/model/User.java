package io.archer.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.sql.Timestamp;
import java.util.UUID;

@Accessors(chain = true)
@Getter
@Setter
public class User {
    private UUID id;
    private String account;
    private String pwd;
    private Timestamp lastLogin;
}
