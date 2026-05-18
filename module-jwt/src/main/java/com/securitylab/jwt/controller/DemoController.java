package com.securitylab.jwt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok("Hola " + user.getUsername() + ", tu token es válido.");
    }

    @GetMapping("/admin")
    public ResponseEntity<String> admin() {
        return ResponseEntity.ok("Endpoint de admin — solo con rol ADMIN");
    }
}