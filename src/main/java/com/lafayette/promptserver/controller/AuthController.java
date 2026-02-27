package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.config.JwtUtil;
import com.lafayette.promptserver.dto.AuthResponse;
import com.lafayette.promptserver.dto.LoginRequest;
import com.lafayette.promptserver.dto.RegisterRequest;
import com.lafayette.promptserver.model.User;
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

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
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

        User user = User.builder()
                .username(req.getUsername())
                .displayName(req.getDisplayName())
                .password(passwordEncoder.encode(req.getPassword()))
                .build();

        userRepository.save(user);

        // Auto-login after registration
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.getUsername());
        String token = jwtUtil.generateToken(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getUsername(), user.getDisplayName(), user.getRoles()));
    }
}
