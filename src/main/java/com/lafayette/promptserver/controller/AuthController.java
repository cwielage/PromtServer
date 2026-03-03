package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.config.JwtUtil;
import com.lafayette.promptserver.dto.AuthResponse;
import com.lafayette.promptserver.dto.LoginRequest;
import com.lafayette.promptserver.dto.RegisterRequest;
import com.lafayette.promptserver.model.InvitationCode;
import com.lafayette.promptserver.model.User;
import com.lafayette.promptserver.repository.InvitationCodeRepository;
import com.lafayette.promptserver.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(req.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByUsername(req.getUsername()).orElseThrow();
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getRoles()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Username already taken"));
        }

        // First registered user gets admin role and skips the invite check.
        // Every subsequent user must provide a valid, unused invitation code.
        boolean isFirstUser = userRepository.count() == 0;
        InvitationCode invite = null;

        if (!isFirstUser) {
            String code = req.getInvitationCode();
            if (code == null || code.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "An invitation code is required to register"));
            }
            invite = invitationCodeRepository.findByCode(code).orElse(null);
            if (invite == null || invite.isUsed()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Invalid or already used invitation code"));
            }
        }

        User user = User.builder()
                .username(req.getUsername())
                .displayName(req.getDisplayName())
                .password(passwordEncoder.encode(req.getPassword()))
                .roles(isFirstUser ? List.of("ROLE_USER", "ROLE_ADMIN") : List.of("ROLE_USER"))
                .build();

        userRepository.save(user);

        // Mark the invitation code as consumed
        if (invite != null) {
            invite.setUsed(true);
            invite.setUsedBy(user.getUsername());
            invite.setUsedAt(LocalDateTime.now());
            invitationCodeRepository.save(invite);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(req.getUsername());
        String token = jwtUtil.generateToken(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getRoles()));
    }
}
