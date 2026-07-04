package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "백오피스 회원 상세 (역할별 프로필 포함)")
public record BackofficeMemberDetailDTO(
        @Schema(description = "회원 공통 정보") BackofficeMemberBaseDetailDTO base,
        @Schema(description = "학생 프로필 (STUDENT일 때만)") BackofficeStudentProfileDetailDTO student,
        @Schema(description = "관리자 프로필 (ADMIN일 때만)") BackofficeAdminProfileDetailDTO admin,
        @Schema(description = "제휴업체 프로필 (PARTNER일 때만)") BackofficePartnerProfileDetailDTO partner
) {
    public static BackofficeMemberDetailDTO fromStudent(Member member) {
        return new BackofficeMemberDetailDTO(
                BackofficeMemberBaseDetailDTO.from(member),
                BackofficeStudentProfileDetailDTO.from(member),
                null,
                null
        );
    }

    public static BackofficeMemberDetailDTO fromAdmin(Member member) {
        return new BackofficeMemberDetailDTO(
                BackofficeMemberBaseDetailDTO.from(member),
                null,
                BackofficeAdminProfileDetailDTO.from(member),
                null
        );
    }

    public static BackofficeMemberDetailDTO fromPartner(Member member) {
        return new BackofficeMemberDetailDTO(
                BackofficeMemberBaseDetailDTO.from(member),
                null,
                null,
                BackofficePartnerProfileDetailDTO.from(member)
        );
    }
}
