package com.securitylab.cognito.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/public/hello")
    public ResponseEntity<String> publicHello() {
        return ResponseEntity.ok("Endpoint público — sin token necesario");
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello(@AuthenticationPrincipal Jwt jwt) {
        // 💡 Cognito Access Token usa "username", no "cognito:username"
        // El valor es el UUID interno del usuario, no el email
        String username = jwt.getClaimAsString("username");
        return ResponseEntity.ok("Hola " + username + " — tu token de Cognito es válido");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminOnly(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("username");
        return ResponseEntity.ok("Área admin — usuario: " + username);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        // 💡 El Access Token de Cognito NO incluye email — eso solo viene en el ID Token
        // Por eso usamos getOrDefault para evitar el 500
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");

        return ResponseEntity.ok(Map.of(
            "subject",    jwt.getSubject(),
            "username",   jwt.getClaimAsString("username"),
            "tokenUse",   jwt.getClaimAsString("token_use"),   // "access"
            "groups",     groups != null ? groups : List.of(),
            "clientId",   jwt.getClaimAsString("client_id"),
            "expiresAt",  jwt.getExpiresAt().toString()
        ));
    }
}