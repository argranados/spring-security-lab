// module-jwt/src/main/java/com/securitylab/jwt/model/AuthResponse.java
package com.securitylab.jwt.model;

public record AuthResponse(
    String accessToken,
    String refreshToken
) {}