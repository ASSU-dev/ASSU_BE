package com.assu.server.domain.auth.dto.signup.student;

import com.assu.server.domain.user.entity.enums.University;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학생 토큰 인증 페이로드")
public record StudentTokenAuthPayloadDTO(
        @Schema(description = "유세인트 sToken", example = "Vy3zFySFx5FASz175Kx7AzKyuSFQEgQ...")
        @NotNull(message = "sToken은 필수입니다.")
        @JsonProperty(value = "sToken")
        String sToken,
        
        @Schema(description = "유세인트 sIdno", example = "20211438")
        @NotNull(message = "sIdno는 필수입니다.")
        @JsonProperty(value = "sIdno")
        String sIdno,
        
        @Schema(description = "대학교", example = "SSU")
        University university
) {
}
