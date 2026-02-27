package com.lafayette.promptserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "prompts")
@CompoundIndex(name = "category_author_idx", def = "{'category': 1, 'author': 1}")
public class Prompt {

    @Id
    private String id;

    /** Human-readable title of the prompt */
    @TextIndexed(weight = 3)
    private String title;

    /** The actual prompt content */
    @TextIndexed(weight = 2)
    private String content;

    /**
     * SHA-256 hex digest of the current content.
     * Changes with every edit — useful for detecting duplicates.
     */
    private String contentHash;

    /** Creator / owner of the prompt */
    private String author;

    /** Searchable keywords / tags */
    @TextIndexed(weight = 2)
    @Builder.Default
    private List<String> keywords = new ArrayList<>();

    /** Logical grouping category (e.g. "coding", "writing", "summarisation") */
    private String category;

    // ---------------------------------------------------------------
    // Rating  (1–5 stars, averaged over all submitted ratings)
    // ---------------------------------------------------------------

    /** Current average star rating (0.0 if not yet rated) */
    @Builder.Default
    private double rating = 0.0;

    /** How many individual ratings have been submitted */
    @Builder.Default
    private int ratingCount = 0;

    // ---------------------------------------------------------------
    // Timestamps — populated automatically by @EnableMongoAuditing
    // ---------------------------------------------------------------

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;

    // ---------------------------------------------------------------
    // Git-like version history
    // ---------------------------------------------------------------

    /**
     * Full version history of this prompt.
     * Version 1 is the initial creation; each update appends a new entry.
     */
    @Builder.Default
    private List<PromptVersion> versions = new ArrayList<>();
}
