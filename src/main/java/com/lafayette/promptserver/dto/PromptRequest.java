package com.lafayette.promptserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Payload for creating or updating a prompt. */
@Data
public class PromptRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @NotBlank(message = "Content must not be blank")
    private String content;

    @NotBlank(message = "Author must not be blank")
    private String author;

    private List<String> keywords;

    private String category;

    /**
     * Optional commit message describing the change.
     * Defaults to "Initial version" on create and "Updated" on update
     * when not provided.
     */
    private String commitMessage;
}
