package com.lafayette.promptserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        String authHeader = request.getHeader("Authorization");

        log.info("JwtAuthFilter [{} {}] Authorization: {}", method, uri,
                authHeader != null ? authHeader.substring(0, Math.min(30, authHeader.length())) + "..." : "MISSING");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("JwtAuthFilter [{} {}] no Bearer token — continuing unauthenticated", method, uri);
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String username;

        try {
            username = jwtUtil.extractUsername(token);
            log.info("JwtAuthFilter [{} {}] extracted username: {}", method, uri, username);
        } catch (Exception e) {
            log.warn("JwtAuthFilter [{} {}] extractUsername failed: {}", method, uri, e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtUtil.isValid(token, userDetails)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("JwtAuthFilter [{} {}] authenticated user: {} roles: {}", method, uri, username, userDetails.getAuthorities());
                } else {
                    log.warn("JwtAuthFilter [{} {}] isValid=false for user: {}", method, uri, username);
                }
            } catch (Exception e) {
                log.warn("JwtAuthFilter [{} {}] user lookup failed for {}: {}", method, uri, username, e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }
}
