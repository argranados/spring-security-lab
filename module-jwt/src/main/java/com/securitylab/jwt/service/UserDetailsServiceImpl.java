package com.securitylab.jwt.service;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // Por ahora usuario hardcodeado — luego lo conectas a la BD
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if ("admin".equals(username)) {
            return User.builder()
                    .username("admin")
                    // contraseña: "password" encriptada con BCrypt
                    .password("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG")
                    .roles("ADMIN")
                    .build();
        }
        throw new UsernameNotFoundException("Usuario no encontrado: " + username);
    }
}