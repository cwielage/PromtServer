package com.lafayette.promptserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String displayName;
    private List<String> roles;
    private String tenantId;
    private String tenantName;
}
