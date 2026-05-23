package com.securitylab.sessions.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import com.securitylab.sessions.service.UserDetailsServiceImpl;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService; // ← agrega
    private final PasswordEncoder passwordEncoder; // ← agrega

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String username = body.get("username");
        String password = body.get("password");

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            return ResponseEntity.status(401).body("Bad credentials");
        }

        // 💡 Crear el token SIN credenciales — el tercer argumento null borra el
        // password
        // del objeto Authentication que se guarda en Redis
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null, // ← null aquí = no guardar el password en la sesión
                userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "sessionId", session.getId(),
                "username", username));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "roles", authentication.getAuthorities()));
    }

    @GetMapping("/hash")
    public String generateHash(@RequestParam String raw) {
        return passwordEncoder.encode(raw);
    }

    // En AuthController.java — agrega este método
    @GetMapping("/sessions")
    public ResponseEntity<?> activeSessions(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).body("No active session");
        }
        return ResponseEntity.ok(Map.of(
                "sessionId", session.getId(),
                "createdAt", session.getCreationTime(),
                "lastAccessed", session.getLastAccessedTime(),
                "maxInactiveInterval", session.getMaxInactiveInterval() + " seconds"));
    }
}