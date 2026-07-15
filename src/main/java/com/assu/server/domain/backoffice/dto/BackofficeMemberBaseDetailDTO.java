package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백오피스 회원 공통 상세")
public record BackofficeMemberBaseDetailDTO(
        @Schema(description = "회원 ID") Long memberId,
        @Schema(description = "회원 역할") UserRole role,
        @Schema(description = "활성화 상태") ActivationStatus status,
        @Schema(description = "탈퇴 시각") LocalDateTime deletedAt,
        @Schema(description = "가입 시각") LocalDateTime createdAt,
        @Schema(description = "위치 정보 수집 동의") Boolean isLocationTermAgreed,
        @Schema(description = "마케팅 수신 동의") Boolean isMarketingTermAgreed
) {
    public static BackofficeMemberBaseDetailDTO from(Member member) {
        return new BackofficeMemberBaseDetailDTO(
                member.getId(),
                member.getRole(),
                member.getIsActivated(),
                member.getDeletedAt(),
                member.getCreatedAt(),
                member.getIsLocationTermAgreed(),
                member.getIsMarketingTermAgreed()
        );
    }
}
