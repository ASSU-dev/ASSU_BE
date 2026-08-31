package com.assu.server.domain.auth.dto.signup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "제휴업체 단체 가입 개별 요청 정보")
public record PartnerBatchSignUpItemDTO(
        @Schema(description = "이메일 주소", example = "partner1@example.com")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @Schema(description = "비밀번호(평문)", example = "Password123!")
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @Schema(description = "업체명", example = "숭실카페 1호점")
        @Size(min = 1, max = 50, message = "업체명은 1~50자여야 합니다.")
        @NotBlank(message = "업체명은 필수입니다.")
        String name,

        @Schema(description = "도로명 주소", example = "서울특별시 동작구 상도로 369")
        String roadAddress,

        @Schema(description = "위도", example = "37.4963")
        Double latitude,

        @Schema(description = "경도", example = "126.9573")
        Double longitude
) {
}
