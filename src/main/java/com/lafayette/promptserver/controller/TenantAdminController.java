package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.config.JwtUtil;
import com.lafayette.promptserver.dto.UserSummaryDto;
import com.lafayette.promptserver.model.InvitationCode;
import com.lafayette.promptserver.model.Tenant;
import com.lafayette.promptserver.model.User;
import com.lafayette.promptserver.repository.InvitationCodeRepository;
import com.lafayette.promptserver.repository.TenantRepository;
import com.lafayette.promptserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant-admin")
@RequiredArgsConstructor
public class TenantAdminController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final JwtUtil jwtUtil;

    // ---------------------------------------------------------------
    // Users in own tenant
    // ---------------------------------------------------------------

    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryDto>> listUsers(HttpServletRequest request) {
        String tenantId = requireTenantId(request);
        String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);

        List<UserSummaryDto> users = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getDisplayName(),
                        u.getRoles(), u.getCreatedAt(), tenantId, tenantName))
                .toList();

        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<UserSummaryDto> toggleTenantAdmin(@PathVariable String id,
                                                             @RequestBody Map<String, Boolean> body,
                                                             HttpServletRequest request) {
        String tenantId = requireTenantId(request);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!tenantId.equals(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not belong to your tenant");
        }

        boolean makeTenantAdmin = Boolean.TRUE.equals(body.get("tenantAdmin"));
        List<String> roles = new java.util.ArrayList<>(user.getRoles());
        if (makeTenantAdmin) {
            if (!roles.contains("ROLE_TENANT_ADMIN")) roles.add("ROLE_TENANT_ADMIN");
        } else {
            roles.remove("ROLE_TENANT_ADMIN");
        }
        user.setRoles(roles);
        userRepository.save(user);

        String tenantName = tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
        return ResponseEntity.ok(new UserSummaryDto(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRoles(), user.getCreatedAt(), tenantId, tenantName));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id, HttpServletRequest request) {
        String tenantId = requireTenantId(request);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!tenantId.equals(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not belong to your tenant");
        }

        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Invitations for own tenant
    // ---------------------------------------------------------------

    @PostMapping("/invitations")
    public ResponseEntity<InvitationCode> createInvitation(HttpServletRequest request,
                                                            Authentication authentication) {
        String tenantId = requireTenantId(request);

        InvitationCode code = InvitationCode.builder()
                .code(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .createdBy(authentication.getName())
                .tenantId(tenantId)
                .used(false)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(invitationCodeRepository.save(code));
    }

    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationCode>> listInvitations(HttpServletRequest request) {
        String tenantId = requireTenantId(request);
        List<InvitationCode> codes = invitationCodeRepository.findAll().stream()
                .filter(c -> tenantId.equals(c.getTenantId()))
                .toList();
        return ResponseEntity.ok(codes);
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private String requireTenantId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        String tenantId = jwtUtil.extractTenantId(header.substring(7));
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant assigned");
        }
        return tenantId;
    }
}
