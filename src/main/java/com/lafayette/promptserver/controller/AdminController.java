package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.dto.CreateTenantResponse;
import com.lafayette.promptserver.dto.ResetPasswordRequest;
import com.lafayette.promptserver.dto.UpdateUserRequest;
import com.lafayette.promptserver.dto.UserSummaryDto;
import com.lafayette.promptserver.model.InvitationCode;
import com.lafayette.promptserver.model.Tenant;
import com.lafayette.promptserver.model.User;
import com.lafayette.promptserver.repository.InvitationCodeRepository;
import com.lafayette.promptserver.repository.TenantRepository;
import com.lafayette.promptserver.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> listUsers() {
        List<UserSummaryDto> users = userRepository.findAll()
                .stream()
                .map(u -> toDto(u))
                .sorted(Comparator.comparing(UserSummaryDto::getUsername))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserSummaryDto> updateUser(@PathVariable String id,
                                                     @Valid @RequestBody UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setUsername(req.getUsername());
        user.setDisplayName(req.getDisplayName());
        user.setRoles(req.getRoles());
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id,
                                                             @Valid @RequestBody ResetPasswordRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Tenants
    // ---------------------------------------------------------------

    @GetMapping("/tenants")
    public ResponseEntity<List<Tenant>> listTenants() {
        return ResponseEntity.ok(tenantRepository.findAllByOrderByNameAsc());
    }

    @PostMapping("/tenants")
    public ResponseEntity<CreateTenantResponse> createTenant(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant name is required");
        }
        if (tenantRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A tenant with this name already exists");
        }

        // Create tenant
        Tenant tenant = tenantRepository.save(Tenant.builder().name(name).build());

        // Generate a unique admin username derived from the tenant name
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String baseUsername = slug + "-admin";
        String adminUsername = baseUsername;
        int suffix = 2;
        while (userRepository.existsByUsername(adminUsername)) {
            adminUsername = baseUsername + suffix++;
        }

        // Generate a readable temporary password (letters + digits, no ambiguous chars)
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder tempPwd = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            tempPwd.append(chars.charAt(rng.nextInt(chars.length())));
        }
        String adminTempPassword = tempPwd.toString();

        User adminUser = User.builder()
                .username(adminUsername)
                .displayName(name + " Admin")
                .password(passwordEncoder.encode(adminTempPassword))
                .roles(List.of("ROLE_USER", "ROLE_TENANT_ADMIN"))
                .tenantId(tenant.getId())
                .mustChangePassword(true)
                .build();
        userRepository.save(adminUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateTenantResponse(tenant, adminUsername, adminTempPassword));
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        if (!tenantRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        tenantRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Invitation codes (with tenantId)
    // ---------------------------------------------------------------

    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationCode>> listInvitations() {
        return ResponseEntity.ok(invitationCodeRepository.findAll());
    }

    @PostMapping("/invitations")
    public ResponseEntity<InvitationCode> createInvitation(@RequestBody Map<String, String> body,
                                                            Authentication authentication) {
        String tenantId = body.get("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        if (!tenantRepository.existsById(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        InvitationCode code = InvitationCode.builder()
                .code(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .createdBy(authentication.getName())
                .tenantId(tenantId)
                .used(false)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationCodeRepository.save(code));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private UserSummaryDto toDto(User u) {
        String tenantName = u.getTenantId() == null ? null :
                tenantRepository.findById(u.getTenantId()).map(Tenant::getName).orElse(null);
        return new UserSummaryDto(u.getId(), u.getUsername(), u.getDisplayName(),
                u.getRoles(), u.getCreatedAt(), u.getTenantId(), tenantName);
    }
}
