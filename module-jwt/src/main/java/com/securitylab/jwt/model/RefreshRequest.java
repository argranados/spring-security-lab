// module-jwt/src/main/java/com/securitylab/jwt/model/RefreshRequest.java
package com.securitylab.jwt.model;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "Refresh token es requerido")
    String refreshToken
) {}