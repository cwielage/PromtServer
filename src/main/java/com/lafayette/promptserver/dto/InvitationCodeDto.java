package com.lafayette.promptserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InvitationCodeDto {

    private String id;
    private String code;
    private String createdBy;
    private boolean used;
    private String usedBy;
    private LocalDateTime createdAt;
    private LocalDateTime usedAt;
}
