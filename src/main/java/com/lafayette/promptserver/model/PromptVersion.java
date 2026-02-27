package com.lafayette.promptserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single version of a prompt (git-like history).
 * Embedded in the Prompt document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVersion {

    /** Monotonically increasing version number (1, 2, 3, ...) */
    private int versionNumber;

    /** Full content snapshot of this version */
    private String content;

    /** SHA-256 hex of the content — acts as the "commit hash" */
    private String contentHash;

    /** Who authored this version */
    private String author;

    /** Short description of what changed — analogous to a git commit message */
    private String commitMessage;

    /** When this version was saved */
    private LocalDateTime timestamp;
}
