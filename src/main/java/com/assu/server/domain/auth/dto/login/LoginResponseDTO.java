package com.assu.server.domain.auth.dto.login;

import com.assu.server.domain.auth.dto.common.UserBasicInfoDTO;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 성공 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponseDTO(
        @Schema(description = "회원 ID", example = "123")
        Long memberId,
        
        @Schema(description = "회원 역할", example = "STUDENT")
        UserRole role,
        
        @Schema(description = "회원 상태", example = "SUSPEND")
        ActivationStatus status,
        
        @Schema(description = "액세스 토큰/리프레시 토큰", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        TokensDTO tokens,
        
        @Schema(description = "사용자 기본 정보 (캐싱용)")
        UserBasicInfoDTO basicInfo
) {
    public static LoginResponseDTO from(Member member, TokensDTO tokens) {
        return new LoginResponseDTO(
                member.getId(),
                member.getRole(),
                member.getIsActivated(),
                tokens,
                UserBasicInfoDTO.from(member)
        );
    }
}
