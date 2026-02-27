package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.dto.ResetPasswordRequest;
import com.lafayette.promptserver.dto.UpdateUserRequest;
import com.lafayette.promptserver.dto.UserSummaryDto;
import com.lafayette.promptserver.model.User;
import com.lafayette.promptserver.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserSummaryDto>> listUsers() {
        List<UserSummaryDto> users = userRepository.findAll()
                .stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getDisplayName(), u.getRoles(), u.getCreatedAt()))
                .sorted(Comparator.comparing(UserSummaryDto::getUsername))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserSummaryDto> updateUser(@PathVariable String id,
                                                     @Valid @RequestBody UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setUsername(req.getUsername());
        user.setDisplayName(req.getDisplayName());
        user.setRoles(req.getRoles());
        userRepository.save(user);

        return ResponseEntity.ok(
                new UserSummaryDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoles(), user.getCreatedAt())
        );
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id,
                                                             @Valid @RequestBody ResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
