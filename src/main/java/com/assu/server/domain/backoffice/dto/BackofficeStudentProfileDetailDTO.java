package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.EnrollmentStatus;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 학생 프로필 상세")
public record BackofficeStudentProfileDetailDTO(
        @Schema(description = "이름") String name,
        @Schema(description = "학번") String studentNumber,
        @Schema(description = "대학교") University university,
        @Schema(description = "단과대") Department department,
        @Schema(description = "전공") Major major,
        @Schema(description = "재학 상태") EnrollmentStatus enrollmentStatus,
        @Schema(description = "학년/학기") String yearSemester,
        @Schema(description = "신고 상태") ReportedStatus reportedStatus,
        @Schema(description = "스탬프") Integer stamp
) {
    public static BackofficeStudentProfileDetailDTO from(Member member) {
        Student student = member.getStudentProfile();
        if (student == null) {
            return null;
        }

        String studentNumber = member.getSsuAuth() != null ? member.getSsuAuth().getStudentNumber() : null;

        return new BackofficeStudentProfileDetailDTO(
                student.getName(),
                studentNumber,
                student.getUniversity(),
                student.getDepartment(),
                student.getMajor(),
                student.getEnrollmentStatus(),
                student.getYearSemester(),
                student.getStatus(),
                student.getStamp()
        );
    }
}
