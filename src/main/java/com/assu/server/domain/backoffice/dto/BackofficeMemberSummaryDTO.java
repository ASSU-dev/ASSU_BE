package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백오피스 회원 목록 요약")
public record BackofficeMemberSummaryDTO(
        @Schema(description = "회원 ID") Long memberId,
        @Schema(description = "회원 역할") UserRole role,
        @Schema(description = "활성화 상태") ActivationStatus status,
        @Schema(description = "이름/업체명/단체명") String name,
        @Schema(description = "이메일 (CommonAuth)") String email,
        @Schema(description = "학번 (SSUAuth, Student만)") String studentNumber,
        @Schema(description = "탈퇴 시각") LocalDateTime deletedAt,
        @Schema(description = "가입 시각") LocalDateTime createdAt,
        @Schema(description = "사업자등록증 검증 여부 (Partner)") Boolean isLicenseVerified,
        @Schema(description = "인감 검증 여부 (Admin)") Boolean isSignVerified
) {
    public static BackofficeMemberSummaryDTO from(Member member) {
        String name = member.resolveName();
        String email = member.getCommonAuth() != null ? member.getCommonAuth().getEmail() : null;
        String studentNumber = member.getSsuAuth() != null ? member.getSsuAuth().getStudentNumber() : null;
        Boolean isLicenseVerified = member.getPartnerProfile() != null
                ? member.getPartnerProfile().getIsLicenseVerified() : null;
        Boolean isSignVerified = member.getAdminProfile() != null
                ? member.getAdminProfile().getIsSignVerified() : null;

        return new BackofficeMemberSummaryDTO(
                member.getId(),
                member.getRole(),
                member.getIsActivated(),
                name,
                email,
                studentNumber,
                member.getDeletedAt(),
                member.getCreatedAt(),
                isLicenseVerified,
                isSignVerified
        );
    }
}
