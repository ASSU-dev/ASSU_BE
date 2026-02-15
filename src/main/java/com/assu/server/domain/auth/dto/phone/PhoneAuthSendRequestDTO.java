package com.assu.server.domain.auth.dto.phone;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "휴대폰 번호 중복가입 확인 및 인증번호 발송 요청")
public record PhoneAuthSendRequestDTO(
        @Schema(description = "인증번호를 받을 휴대폰 번호", example = "01012345678")
        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^010\\d{8}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phoneNumber
) {
}
