package com.assu.server.domain.auth.dto.signup;

import com.assu.server.domain.auth.dto.signup.common.CommonAuthPayloadDTO;
import com.assu.server.domain.auth.dto.signup.common.CommonInfoPayloadDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "관리자 회원가입 요청")
public record AdminSignUpRequestDTO(
        @Schema(description = "휴대폰 번호", example = "01012345678")
        @Pattern(regexp = "^(01[016789])\\d{3,4}\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        String phoneNumber,

        @Schema(description = "마케팅 수신 동의", example = "true")
        @NotNull(message = "마케팅 수신 동의는 필수입니다.")
        Boolean marketingAgree,

        @Schema(description = "위치 정보 수집 동의", example = "true")
        @NotNull(message = "위치 정보 수집 동의는 필수입니다.")
        Boolean locationAgree,
        
        @Schema(description = "관리자/제휴업체공통 인증 정보")
        @Valid
        @NotNull(message = "공통 인증 정보는 필수입니다.")
        CommonAuthPayloadDTO commonAuth,
        
        @Schema(description = "관리자/제휴업체 공통 정보")
        @Valid
        @NotNull(message = "공통 정보는 필수입니다.")
        CommonInfoPayloadDTO commonInfo
) {
}
