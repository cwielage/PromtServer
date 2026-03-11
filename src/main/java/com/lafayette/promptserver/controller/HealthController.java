package com.lafayette.promptserver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public health-check endpoint used by Azure App Service keep-alive pings
 * and load-balancer probes. No authentication required.
 */
@RestController
public class HealthController {

    @GetMapping({"/", "/health"})
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
