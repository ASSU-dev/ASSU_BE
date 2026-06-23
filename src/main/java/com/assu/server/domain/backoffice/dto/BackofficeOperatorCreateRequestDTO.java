package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "백오피스 운영자 생성 요청")
public record BackofficeOperatorCreateRequestDTO(
        @Schema(description = "이메일", example = "ops@assu.app")
        @Email
        @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "P@ssw0rd!")
        @Size(min = 8, max = 72)
        @NotBlank
        String password,

        @Schema(description = "운영자 이름", example = "플랫폼 운영자")
        @NotBlank
        String name
) {
}
