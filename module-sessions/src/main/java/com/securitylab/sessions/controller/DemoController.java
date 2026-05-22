package com.securitylab.sessions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class DemoController {

    @GetMapping("/hello")
    public ResponseEntity<?> hello(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "message", "Hello " + authentication.getName() + "!",
            "roles", authentication.getAuthorities()
        ));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminDashboard() {
        return ResponseEntity.ok(Map.of("message", "Welcome to the admin dashboard"));
    }
}