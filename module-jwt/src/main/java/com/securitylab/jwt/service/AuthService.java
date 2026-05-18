package com.securitylab.jwt.service;

import com.securitylab.jwt.model.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    //  login con AuthenticationManager

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthResponse login(String username, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        UserDetails user = userDetailsService.loadUserByUsername(username);
        return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user)
        );
    }

    public AuthResponse refreshToken(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken);
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (jwtService.isTokenValid(refreshToken, user)) {
            return new AuthResponse(
                    jwtService.generateToken(user),
                    refreshToken
            );
        }
        throw new RuntimeException("Refresh token inválido o expirado");
    }
}