package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /** Returns a sorted list of display names (falls back to username if none set). */
    @GetMapping
    public ResponseEntity<List<String>> listDisplayNames() {
        List<String> names = userRepository.findAll()
                .stream()
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .sorted()
                .toList();
        return ResponseEntity.ok(names);
    }
}
