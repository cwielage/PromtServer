package com.lafayette.promptserver.repository;

import com.lafayette.promptserver.model.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromptRepository extends MongoRepository<Prompt, String> {

    /** Filter by single keyword (exact match inside the keywords list). */
    Page<Prompt> findByKeywordsContainingIgnoreCase(String keyword, Pageable pageable);

    /** Filter by category (case-insensitive). */
    Page<Prompt> findByCategoryIgnoreCase(String category, Pageable pageable);

    /** Filter by author (case-insensitive). */
    Page<Prompt> findByAuthorIgnoreCase(String author, Pageable pageable);

    /** Fetch all distinct categories stored in the collection. */
    List<Prompt> findDistinctByCategory(String category);
}
