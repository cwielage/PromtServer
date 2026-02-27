package com.lafayette.promptserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    /** Human-readable display name shown as author. */
    private String displayName;

    /** BCrypt-hashed password — never stored in plain text. */
    private String password;

    /** e.g. ["ROLE_USER"] or ["ROLE_USER", "ROLE_ADMIN"] */
    @Builder.Default
    private List<String> roles = List.of("ROLE_USER");

    @CreatedDate
    private LocalDateTime createdAt;
}
