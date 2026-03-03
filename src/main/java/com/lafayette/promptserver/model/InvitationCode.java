package com.lafayette.promptserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "invitation_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationCode {

    @Id
    private String id;

    private String code;
    private String createdBy;
    private boolean used;
    private String usedBy;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime usedAt;
}
