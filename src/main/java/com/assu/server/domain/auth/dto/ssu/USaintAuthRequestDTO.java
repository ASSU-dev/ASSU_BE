package com.assu.server.domain.auth.dto.ssu;

import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "유세인트 인증 요청")
public record USaintAuthRequestDTO(
        @Schema(description = "유세인트 sToken", example = "Vy3zFySFx5FASz175Kx7AzKyuSFQEgQ...")
        @NotNull(message = "sToken은 필수입니다.")
        @JsonProperty(value = "sToken")
        String sToken,
        
        @Schema(description = "유세인트 sIdno", example = "20211438")
        @NotNull(message = "sIdno는 필수입니다.")
        @JsonProperty(value = "sIdno")
        String sIdno
) {
    public static USaintAuthRequestDTO from(StudentTokenAuthPayloadDTO payload) {
        return new USaintAuthRequestDTO(payload.sToken(), payload.sIdno());
    }
}
