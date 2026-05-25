package com.assu.server.domain.auth.dto.signup;

import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학생 토큰 회원가입 요청")
public record StudentTokenSignUpRequestDTO(

        @Schema(description = "마케팅 수신 동의", example = "true")
        @NotNull(message = "마케팅 수신 동의는 필수입니다.")
        Boolean marketingAgree,

        @Schema(description = "위치 정보 수집 동의", example = "true")
        @NotNull(message = "위치 정보 수집 동의는 필수입니다.")
        Boolean locationAgree,
        
        @Schema(description = "학생 토큰 인증 정보")
        @Valid
        @NotNull(message = "학생 토큰 인증 정보는 필수입니다.")
        StudentTokenAuthPayloadDTO studentTokenAuth
) {
}
