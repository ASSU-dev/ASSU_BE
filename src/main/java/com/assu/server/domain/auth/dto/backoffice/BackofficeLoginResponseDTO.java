package com.assu.server.domain.auth.dto.backoffice;

import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.dto.common.UserBasicInfoDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 로그인 성공 응답")
public record BackofficeLoginResponseDTO(
        @Schema(description = "회원 ID", example = "1")
        Long memberId,

        @Schema(description = "회원 역할", example = "BACKOFFICE")
        UserRole role,

        @Schema(description = "회원 상태", example = "ACTIVE")
        ActivationStatus status,

        @Schema(description = "액세스/리프레시 토큰")
        TokensDTO tokens,

        @Schema(description = "운영자 기본 정보")
        UserBasicInfoDTO basicInfo
) {
    public static BackofficeLoginResponseDTO from(Member member, TokensDTO tokens) {
        return new BackofficeLoginResponseDTO(
                member.getId(),
                member.getRole(),
                member.getIsActivated(),
                tokens,
                UserBasicInfoDTO.from(member)
        );
    }
}
