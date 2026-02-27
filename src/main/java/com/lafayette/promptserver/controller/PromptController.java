package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.dto.PromptRequest;
import com.lafayette.promptserver.dto.RatingRequest;
import com.lafayette.promptserver.model.Prompt;
import com.lafayette.promptserver.model.PromptVersion;
import com.lafayette.promptserver.service.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    // ---------------------------------------------------------------
    // List / Search  (paginated)
    // ---------------------------------------------------------------

    /**
     * GET /api/prompts
     *   ?q=       full-text search query
     *   &keyword= keyword filter
     *   &category= category filter
     *   &author=  author filter
     *   &page=0   (zero-based page index)
     *   &size=20
     *   &sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<Prompt>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasSearch = (q != null && !q.isBlank())
                || (keyword != null && !keyword.isBlank())
                || (category != null && !category.isBlank())
                || (author != null && !author.isBlank());

        Page<Prompt> result = hasSearch
                ? promptService.search(q, keyword, category, author, pageable)
                : promptService.findAll(pageable);

        return ResponseEntity.ok(result);
    }

    // ---------------------------------------------------------------
    // Single prompt
    // ---------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<Prompt> getById(@PathVariable String id) {
        return ResponseEntity.ok(promptService.findById(id));
    }

    // ---------------------------------------------------------------
    // Create / Update / Delete
    // ---------------------------------------------------------------

    @PostMapping
    public ResponseEntity<Prompt> create(@Valid @RequestBody PromptRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promptService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prompt> update(@PathVariable String id,
                                         @Valid @RequestBody PromptRequest req) {
        return ResponseEntity.ok(promptService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        promptService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Version history (git-like)
    // ---------------------------------------------------------------

    /** Returns the full list of versions — newest last. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<PromptVersion>> history(@PathVariable String id) {
        return ResponseEntity.ok(promptService.getHistory(id));
    }

    /**
     * Reverts the prompt to a specific version number.
     * The revert is recorded as a new version entry.
     */
    @PostMapping("/{id}/revert/{version}")
    public ResponseEntity<Prompt> revert(@PathVariable String id,
                                          @PathVariable int version) {
        return ResponseEntity.ok(promptService.revert(id, version));
    }

    // ---------------------------------------------------------------
    // Rating
    // ---------------------------------------------------------------

    /** Submit a star rating (1–5) for a prompt. */
    @PostMapping("/{id}/rate")
    public ResponseEntity<Prompt> rate(@PathVariable String id,
                                        @Valid @RequestBody RatingRequest req) {
        return ResponseEntity.ok(promptService.rate(id, req.getStars()));
    }

    // ---------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------

    /** Regenerate the AI summary for a prompt via Gemini. */
    @PostMapping("/{id}/summarize")
    public ResponseEntity<Prompt> summarize(@PathVariable String id) {
        return ResponseEntity.ok(promptService.regenerateSummary(id));
    }
}
