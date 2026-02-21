package com.assu.server.domain.auth.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT 토큰 정보")
public record TokensDTO(
        @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,
        
        @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String refreshToken
) {
    public static TokensDTO of(String accessToken, String refreshToken) {
        return new TokensDTO(accessToken, refreshToken);
    }
}
