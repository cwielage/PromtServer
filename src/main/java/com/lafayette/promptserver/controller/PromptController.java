package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.config.JwtUtil;
import com.lafayette.promptserver.dto.PromptRequest;
import com.lafayette.promptserver.dto.RatingRequest;
import com.lafayette.promptserver.model.Prompt;
import com.lafayette.promptserver.model.PromptVersion;
import com.lafayette.promptserver.service.PromptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;
    private final JwtUtil jwtUtil;

    // ---------------------------------------------------------------
    // Tenant resolution helper
    // ---------------------------------------------------------------

    private String requireTenantId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant assigned.");
        }
        try {
            String tenantId = jwtUtil.extractTenantId(header.substring(7));
            if (tenantId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tenant assigned. Contact your administrator.");
            }
            return tenantId;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant assigned.");
        }
    }

    // ---------------------------------------------------------------
    // Meta (distinct values for form dropdowns)
    // ---------------------------------------------------------------

    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> meta(HttpServletRequest request) {
        String tenantId = requireTenantId(request);
        return ResponseEntity.ok(Map.of(
                "categories", promptService.getDistinctCategories(tenantId),
                "keywords",   promptService.getDistinctKeywords(tenantId)
        ));
    }

    // ---------------------------------------------------------------
    // List / Search  (paginated, tenant-scoped)
    // ---------------------------------------------------------------

    @GetMapping
    public ResponseEntity<Page<Prompt>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        String tenantId = requireTenantId(request);

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasSearch = (q != null && !q.isBlank())
                || (keyword != null && !keyword.isBlank())
                || (category != null && !category.isBlank())
                || (author != null && !author.isBlank());

        Page<Prompt> result = hasSearch
                ? promptService.search(tenantId, q, keyword, category, author, pageable)
                : promptService.findAll(tenantId, pageable);

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
    public ResponseEntity<Prompt> create(HttpServletRequest request,
                                         @Valid @RequestBody PromptRequest req) {
        String tenantId = requireTenantId(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(promptService.create(req, tenantId));
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

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PromptVersion>> history(@PathVariable String id) {
        return ResponseEntity.ok(promptService.getHistory(id));
    }

    @PostMapping("/{id}/revert/{version}")
    public ResponseEntity<Prompt> revert(@PathVariable String id,
                                          @PathVariable int version) {
        return ResponseEntity.ok(promptService.revert(id, version));
    }

    // ---------------------------------------------------------------
    // Rating
    // ---------------------------------------------------------------

    @PostMapping("/{id}/rate")
    public ResponseEntity<Prompt> rate(@PathVariable String id,
                                        @Valid @RequestBody RatingRequest req) {
        return ResponseEntity.ok(promptService.rate(id, req.getStars()));
    }

    // ---------------------------------------------------------------
    // Version management
    // ---------------------------------------------------------------

    @DeleteMapping("/{id}/versions/{version}")
    public ResponseEntity<Prompt> deleteVersion(@PathVariable String id,
                                                @PathVariable int version) {
        return ResponseEntity.ok(promptService.deleteVersion(id, version));
    }

    // ---------------------------------------------------------------
    // Summary / Optimize
    // ---------------------------------------------------------------

    @PostMapping("/{id}/summarize")
    public ResponseEntity<Prompt> summarize(@PathVariable String id) {
        return ResponseEntity.ok(promptService.regenerateSummary(id));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<Map<String, String>> optimize(@PathVariable String id) {
        String optimized = promptService.optimizePrompt(id);
        return ResponseEntity.ok(Map.of("optimizedContent", optimized));
    }
}
