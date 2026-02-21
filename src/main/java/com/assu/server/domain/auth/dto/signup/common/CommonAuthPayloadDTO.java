package com.assu.server.domain.auth.dto.signup.common;

import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "공통 인증 정보 페이로드")
public record CommonAuthPayloadDTO(
        @Schema(description = "이메일 주소", example = "user@example.com")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,
        
        @Schema(description = "비밀번호(평문)", example = "P@ssw0rd!")
        @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,
        
        @Schema(description = "단과대", example = "IT공과대학")
        Department department,
        
        @Schema(description = "전공/학과", example = "소프트웨어학부")
        Major major,
        
        @Schema(description = "대학교", example = "SSU")
        University university
) {
}
