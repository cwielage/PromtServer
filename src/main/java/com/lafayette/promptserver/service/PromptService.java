package com.lafayette.promptserver.service;

import com.lafayette.promptserver.dto.PromptRequest;
import com.lafayette.promptserver.model.Prompt;
import com.lafayette.promptserver.model.PromptVersion;
import com.lafayette.promptserver.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptRepository promptRepository;
    private final MongoTemplate mongoTemplate;
    private final GeminiService geminiService;

    // ---------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------

    public Prompt create(PromptRequest req) {
        String hash = sha256(req.getContent());

        PromptVersion initialVersion = PromptVersion.builder()
                .versionNumber(1)
                .content(req.getContent())
                .contentHash(hash)
                .author(req.getAuthor())
                .commitMessage(req.getCommitMessage() != null ? req.getCommitMessage() : "Initial version")
                .timestamp(LocalDateTime.now())
                .build();

        String summary = geminiService.summarize(req.getContent()).orElse(null);

        Prompt prompt = Prompt.builder()
                .title(req.getTitle())
                .content(req.getContent())
                .contentHash(hash)
                .author(req.getAuthor())
                .keywords(req.getKeywords() != null ? req.getKeywords() : new ArrayList<>())
                .category(req.getCategory())
                .summary(summary)
                .versions(new ArrayList<>(List.of(initialVersion)))
                .build();

        return promptRepository.save(prompt);
    }

    public Page<Prompt> findAll(Pageable pageable) {
        return promptRepository.findAll(pageable);
    }

    public Prompt findById(String id) {
        return promptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt not found: " + id));
    }

    public Prompt update(String id, PromptRequest req) {
        Prompt existing = findById(id);

        String newHash = sha256(req.getContent());
        int nextVersion = existing.getVersions().size() + 1;

        PromptVersion newVersion = PromptVersion.builder()
                .versionNumber(nextVersion)
                .content(req.getContent())
                .contentHash(newHash)
                .author(req.getAuthor())
                .commitMessage(req.getCommitMessage() != null ? req.getCommitMessage() : "Updated")
                .timestamp(LocalDateTime.now())
                .build();

        boolean contentChanged = !existing.getContentHash().equals(newHash);

        existing.getVersions().add(newVersion);
        existing.setTitle(req.getTitle());
        existing.setContent(req.getContent());
        existing.setContentHash(newHash);
        // Do NOT update existing.author — keep original creator; version entry tracks who modified
        if (req.getKeywords() != null) {
            existing.setKeywords(req.getKeywords());
        }
        if (req.getCategory() != null) {
            existing.setCategory(req.getCategory());
        }
        // Only call Gemini when content actually changed or there is no summary yet
        if (contentChanged || existing.getSummary() == null || existing.getSummary().isBlank()) {
            geminiService.summarize(req.getContent()).ifPresent(existing::setSummary);
        }

        return promptRepository.save(existing);
    }

    public void delete(String id) {
        if (!promptRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prompt not found: " + id);
        }
        promptRepository.deleteById(id);
    }

    // ---------------------------------------------------------------
    // Version history (git-like)
    // ---------------------------------------------------------------

    public List<PromptVersion> getHistory(String id) {
        return findById(id).getVersions();
    }

    /**
     * Reverts the prompt content to a specific historical version.
     * The revert itself is recorded as a new version entry.
     */
    public Prompt revert(String id, int versionNumber) {
        Prompt prompt = findById(id);

        PromptVersion target = prompt.getVersions().stream()
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Version " + versionNumber + " not found"));

        int nextVersion = prompt.getVersions().size() + 1;
        PromptVersion revertVersion = PromptVersion.builder()
                .versionNumber(nextVersion)
                .content(target.getContent())
                .contentHash(target.getContentHash())
                .author(prompt.getAuthor())
                .commitMessage("Revert to version " + versionNumber)
                .timestamp(LocalDateTime.now())
                .build();

        prompt.getVersions().add(revertVersion);
        prompt.setContent(target.getContent());
        prompt.setContentHash(target.getContentHash());

        return promptRepository.save(prompt);
    }

    // ---------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------

    /**
     * Asks Gemini to suggest an optimised version of the prompt content.
     * Does NOT save anything — the caller decides whether to apply the result.
     */
    public String optimizePrompt(String id) {
        Prompt prompt = findById(id);
        return geminiService.optimize(prompt.getContent())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini API is not configured or returned no result"));
    }

    /** (Re-)generates the AI summary for an existing prompt via Gemini. */
    public Prompt regenerateSummary(String id) {
        Prompt prompt = findById(id);
        String summary = geminiService.summarize(prompt.getContent())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini API is not configured or returned no result"));
        prompt.setSummary(summary);
        return promptRepository.save(prompt);
    }

    // ---------------------------------------------------------------
    // Rating
    // ---------------------------------------------------------------

    public Prompt rate(String id, int stars) {
        Prompt prompt = findById(id);

        // Running average: newAvg = (oldAvg * count + newStars) / (count + 1)
        double newRating = (prompt.getRating() * prompt.getRatingCount() + stars)
                / (prompt.getRatingCount() + 1.0);

        prompt.setRating(Math.round(newRating * 10.0) / 10.0); // one decimal
        prompt.setRatingCount(prompt.getRatingCount() + 1);

        return promptRepository.save(prompt);
    }

    // ---------------------------------------------------------------
    // Search  (full-text + optional filters, with paging)
    // ---------------------------------------------------------------

    /**
     * Searches prompts using MongoDB full-text search.
     * Falls back to a keyword/category/author filter when {@code q} is blank.
     *
     * @param q        free-text query (searches title, content, keywords)
     * @param keyword  exact keyword filter (optional)
     * @param category category filter (optional)
     * @param author   author filter (optional)
     * @param pageable paging + sorting
     */
    public Page<Prompt> search(String q, String keyword, String category,
                               String author, Pageable pageable) {

        boolean hasText = q != null && !q.isBlank();

        Query query = new Query().with(pageable);
        List<Criteria> filters = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            filters.add(Criteria.where("keywords").regex(keyword, "i"));
        }
        if (category != null && !category.isBlank()) {
            filters.add(Criteria.where("category").regex(category, "i"));
        }
        if (author != null && !author.isBlank()) {
            filters.add(Criteria.where("author").regex(author, "i"));
        }

        if (hasText) {
            // MongoDB text search
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matchingAny(q.split("\\s+"));
            query = TextQuery.queryText(textCriteria).sortByScore().with(pageable);
            if (!filters.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
            }
        } else if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }

        List<Prompt> results = mongoTemplate.find(query, Prompt.class);
        long count = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Prompt.class);

        return PageableExecutionUtils.getPage(results, pageable, () -> count);
    }

    // ---------------------------------------------------------------
    // Version management
    // ---------------------------------------------------------------

    /**
     * Deletes a single version entry from the history.
     * If the current (latest) version is deleted, the prompt content rolls back
     * to the new latest version automatically.
     * The last remaining version cannot be deleted.
     */
    public Prompt deleteVersion(String id, int versionNumber) {
        Prompt prompt = findById(id);

        if (prompt.getVersions().size() <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete the only remaining version");
        }

        int latestVersion = prompt.getVersions().stream()
                .mapToInt(PromptVersion::getVersionNumber)
                .max()
                .orElse(1);

        boolean removed = prompt.getVersions().removeIf(v -> v.getVersionNumber() == versionNumber);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Version " + versionNumber + " not found");
        }

        // If the current version was deleted, roll back content to the new latest
        if (versionNumber == latestVersion) {
            PromptVersion newLatest = prompt.getVersions().stream()
                    .max(Comparator.comparingInt(PromptVersion::getVersionNumber))
                    .orElseThrow();
            prompt.setContent(newLatest.getContent());
            prompt.setContentHash(newLatest.getContentHash());
        }

        return promptRepository.save(prompt);
    }

    // ---------------------------------------------------------------
    // Meta (distinct values for form dropdowns)
    // ---------------------------------------------------------------

    /** Returns all distinct non-blank category values, sorted alphabetically. */
    public List<String> getDistinctCategories() {
        return mongoTemplate.findDistinct(new Query(), "category", Prompt.class, String.class)
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    /** Returns all distinct non-blank keyword values, sorted alphabetically. */
    public List<String> getDistinctKeywords() {
        return mongoTemplate.findDistinct(new Query(), "keywords", Prompt.class, String.class)
                .stream()
                .filter(s -> s != null && !s.isBlank())
                .sorted()
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
