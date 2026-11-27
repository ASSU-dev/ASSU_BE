package com.assu.server.domain.student.dto;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.EnrollmentStatus;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "학생 프로필 / 학적 정보 응답 DTO")
public record StudentProfileResponseDTO(
    @Schema(description = "이름", example = "홍길동") String name,
    @Schema(description = "학번", example = "20211234") String studentNumber,
    @Schema(description = "대학교", example = "숭실대학교") String university,
    @Schema(description = "단과대 (명칭)", example = "IT대학") String department,
    @Schema(description = "단과대 (코드)") Department departmentCode,
    @Schema(description = "학과/전공 (명칭)", example = "소프트웨어학부") String major,
    @Schema(description = "학과/전공 (코드)") Major majorCode,
    @Schema(description = "과정/학적 상태", example = "ENROLLED") EnrollmentStatus enrollmentStatus,
    @Schema(description = "학년/학기", example = "3학년 1학기") String yearSemester
) {
    public static StudentProfileResponseDTO from(Member member) {
        Student student = member.getStudentProfile();
        String studentNumber = member.getSsuAuth() != null ? member.getSsuAuth().getStudentNumber() : null;

        return StudentProfileResponseDTO.builder()
                .name(student != null ? student.getName() : null)
                .studentNumber(studentNumber)
                .university(student != null && student.getUniversity() != null ? student.getUniversity().getDisplayName() : null)
                .department(student != null && student.getDepartment() != null ? student.getDepartment().getDisplayName() : null)
                .departmentCode(student != null ? student.getDepartment() : null)
                .major(student != null && student.getMajor() != null ? student.getMajor().getDisplayName() : null)
                .majorCode(student != null ? student.getMajor() : null)
                .enrollmentStatus(student != null ? student.getEnrollmentStatus() : null)
                .yearSemester(student != null ? student.getYearSemester() : null)
                .build();
    }
}
