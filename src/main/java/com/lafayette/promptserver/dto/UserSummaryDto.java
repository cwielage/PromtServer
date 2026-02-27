package com.lafayette.promptserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class UserSummaryDto {
    private String id;
    private String username;
    private String displayName;
    private List<String> roles;
    private LocalDateTime createdAt;
}
