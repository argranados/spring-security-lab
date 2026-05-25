package com.securitylab.keycloak.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    // Sin token — acceso público
    @GetMapping("/public/hello")
    public ResponseEntity<String> publicHello() {
        return ResponseEntity.ok("Endpoint público — sin token necesario");
    }

    // Cualquier usuario autenticado
    @GetMapping("/hello")
    public ResponseEntity<String> hello(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Hola " + jwt.getClaimAsString("preferred_username")
                + " — tu token de Keycloak es válido");
    }

    // Solo ROLE_ADMIN
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<String> adminOnly(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok("Área admin — usuario: "
                + jwt.getClaimAsString("preferred_username"));
    }

    // Ver todos los claims del token
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
            "subject",   jwt.getSubject(),
            "username",  jwt.getClaimAsString("preferred_username"),
            "email",     jwt.getClaimAsString("email"),
            "roles",     jwt.getClaimAsMap("realm_access"),
            "expiresAt", jwt.getExpiresAt().toString()
        ));
    }
}