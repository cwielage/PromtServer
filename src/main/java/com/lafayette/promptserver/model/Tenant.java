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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenants")
public class Tenant {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;
}
