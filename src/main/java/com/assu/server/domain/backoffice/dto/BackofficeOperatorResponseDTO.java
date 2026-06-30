package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.backoffice.entity.BackofficeUser;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 운영자 응답")
public record BackofficeOperatorResponseDTO(
        @Schema(description = "회원 ID")
        Long memberId,

        @Schema(description = "이메일")
        String email,

        @Schema(description = "운영자 이름")
        String name,

        @Schema(description = "활성 상태")
        ActivationStatus status
) {
    public static BackofficeOperatorResponseDTO from(Member member, BackofficeUser backofficeUser) {
        return new BackofficeOperatorResponseDTO(
                member.getId(),
                member.getCommonAuth().getEmail(),
                backofficeUser.getName(),
                member.getIsActivated()
        );
    }
}
