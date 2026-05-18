// module-jwt/src/main/java/com/securitylab/jwt/model/LoginRequest.java
package com.securitylab.jwt.model;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Username es requerido")
    String username,

    @NotBlank(message = "Password es requerido")
    String password
) {}