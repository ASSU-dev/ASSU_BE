package com.assu.server.domain.auth.dto.signup;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.common.UserBasicInfoDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 성공 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignUpResponseDTO(
        @Schema(description = "회원 ID", example = "123")
        Long memberId,
        
        @Schema(description = "회원 역할", example = "STUDENT")
        UserRole role,
        
        @Schema(description = "회원 상태", example = "ACTIVE")
        ActivationStatus status,
        
        @Schema(description = "액세스 토큰/리프레시 토큰")
        TokensDTO tokens,
        
        @Schema(description = "사용자 기본 정보 (캐싱용)")
        UserBasicInfoDTO basicInfo
) {
    public static SignUpResponseDTO from(Member member, TokensDTO tokens) {
        return new SignUpResponseDTO(
                member.getId(),
                member.getRole(),
                member.getIsActivated(),
                tokens,
                UserBasicInfoDTO.from(member)
        );
    }
}
