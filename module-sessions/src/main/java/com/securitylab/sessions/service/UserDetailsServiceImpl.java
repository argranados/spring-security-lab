package com.securitylab.sessions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // En memoria por ahora — igual que hiciste en module-jwt
    // Después puedes conectarlo a BD con un UserRepository
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return switch (username) {
            case "admin" -> User.builder()
                    .username("admin")
                    .password("$2a$10$qtJvmt.1BAxw/46A4oPzw.zl8R0rdTlPy.rqTNXqXHWElEwPTSUXC") // "password"
                    .authorities(List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_USER")
                    ))
                    .build();
            case "user" -> User.builder()
                    .username("user")
                    .password("$2a$10$qtJvmt.1BAxw/46A4oPzw.zl8R0rdTlPy.rqTNXqXHWElEwPTSUXC") // "password"
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
            default -> throw new UsernameNotFoundException("User not found: " + username);
        };
    }
}

// curl "http://localhost:8085/auth/hash?raw=password"
// Copia ese hash exacto, ponlo en el UserDetailsServiceImpl, reinicia y prueba. Esto garantiza que el hash lo genera el mismo BCryptPasswordEncoder que luego lo va a verificar — sin diferencias de prefijo ni de versión.