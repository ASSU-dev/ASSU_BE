package com.assu.server.domain.auth.dto.common;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.user.entity.Student;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 기본 정보")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserBasicInfoDTO(
        @Schema(description = "이름/업체명/단체명", example = "홍길동")
        String name,
        
        @Schema(description = "대학교", example = "숭실대학교")
        String university,
        
        @Schema(description = "단과대", example = "IT공과대학")
        String department,
        
        @Schema(description = "전공/학과", example = "소프트웨어학부")
        String major
) {
    public static UserBasicInfoDTO from(Member member) {
        String name = null;
        String university = null;
        String department = null;
        String major = null;

        switch (member.getRole()) {
            case STUDENT -> {
                Student student = member.getStudentProfile();
                if (student != null) {
                    name = student.getName();
                    university = student.getUniversity().getDisplayName();
                    department = student.getDepartment().getDisplayName();
                    major = student.getMajor().getDisplayName();
                }
            }
            case ADMIN -> {
                var admin = member.getAdminProfile();
                if (admin != null) {
                    name = admin.getName();
                    university = admin.getUniversity() != null ? admin.getUniversity().getDisplayName() : null;
                    department = admin.getDepartment() != null ? admin.getDepartment().getDisplayName() : null;
                    major = admin.getMajor() != null ? admin.getMajor().getDisplayName() : null;
                }
            }
            case PARTNER -> {
                var partner = member.getPartnerProfile();
                if (partner != null) {
                    name = partner.getName();
                }
            }
        }

        return new UserBasicInfoDTO(name, university, department, major);
    }
}
