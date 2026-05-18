package com.securitylab.jwt.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public Map<String, String> login(String username, String password) {
        // Spring valida las credenciales — lanza excepción si son incorrectas
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        UserDetails user = userDetailsService.loadUserByUsername(username);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", jwtService.generateToken(user));
        tokens.put("refreshToken", jwtService.generateRefreshToken(user));
        return tokens;
    }

    public Map<String, String> refreshToken(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken);
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (jwtService.isTokenValid(refreshToken, user)) {
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", jwtService.generateToken(user));
            tokens.put("refreshToken", refreshToken); // reutiliza el mismo refresh
            return tokens;
        }
        throw new RuntimeException("Refresh token inválido o expirado");
    }
}