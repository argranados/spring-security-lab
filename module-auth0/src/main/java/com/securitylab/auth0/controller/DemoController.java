package com.securitylab.auth0.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/public/hello")
    public ResponseEntity<String> publicHello() {
        return ResponseEntity.ok("Endpoint público — sin token necesario");
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Hola " + jwt.getSubject()
                + " — tu token de Auth0 es válido");
    }

    // Auth0 usa "admin:data" en lugar de "ROLE_ADMIN"
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('admin:data')")
    public ResponseEntity<String> adminOnly(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Área admin — subject: " + jwt.getSubject());
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
            "subject",      jwt.getSubject(),
            "audience",     jwt.getAudience(),
            "permissions",  jwt.getClaimAsStringList("permissions"),
            "expiresAt",    jwt.getExpiresAt().toString()
        ));
    }
}