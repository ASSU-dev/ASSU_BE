package com.assu.server.domain.auth.dto.login;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "액세스 토큰 갱신 응답")
public record RefreshResponseDTO(
        @Schema(description = "회원 ID", example = "123")
        Long memberId,
        @Schema(description = "새로운 액세스 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String newAccess,
        @Schema(description = "새로운 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String newRefresh
) {
    public static RefreshResponseDTO from(Long memberId, TokensDTO tokens) {
        return new RefreshResponseDTO(
                memberId,
                tokens.accessToken(),
                tokens.refreshToken()
        );
    }
}
