package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백오피스 관리자 프로필 상세")
public record BackofficeAdminProfileDetailDTO(
        @Schema(description = "이름") String name,
        @Schema(description = "이메일") String email,
        @Schema(description = "전화번호") String phoneNum,
        @Schema(description = "사무실 주소") String officeAddress,
        @Schema(description = "상세 주소") String detailAddress,
        @Schema(description = "대학교") University university,
        @Schema(description = "단과대") Department department,
        @Schema(description = "전공") Major major,
        @Schema(description = "인감 검증 여부") Boolean isSignVerified,
        @Schema(description = "인감 검증 시각") LocalDateTime signVerifiedAt
) {
    public static BackofficeAdminProfileDetailDTO from(Member member) {
        Admin admin = member.getAdminProfile();
        if (admin == null) {
            return null;
        }

        String email = member.getCommonAuth() != null ? member.getCommonAuth().getEmail() : null;

        return new BackofficeAdminProfileDetailDTO(
                admin.getName(),
                email,
                admin.getPhoneNum(),
                admin.getOfficeAddress(),
                admin.getDetailAddress(),
                admin.getUniversity(),
                admin.getDepartment(),
                admin.getMajor(),
                admin.getIsSignVerified(),
                admin.getSignVerifiedAt()
        );
    }
}
