package com.assu.server.domain.suggestion.dto;

import com.assu.server.domain.admin.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;

public record GetSuggestionAdminsDTO(
        @Schema(description = "총학생회 ID", example = "1")
        Long adminId,

        @Schema(description = "총학생회 이름", example = "숭실대학교 총학생회")
        String adminName,

        @Schema(description = "단과대학 학생회 ID", example = "2")
        Long departId,

        @Schema(description = "단과대학 학생회 이름", example = "IT대학 학생회")
        String departName,

        @Schema(description = "학부/학과 학생회 ID", example = "3")
        Long majorId,

        @Schema(description = "학부/학과 학생회 이름", example = "컴퓨터학부 학생회")
        String majorName
) {
    public static GetSuggestionAdminsDTO of(Admin universityAdmin, Admin departmentAdmin, Admin majorAdmin) {
        return new GetSuggestionAdminsDTO(
                universityAdmin != null ? universityAdmin.getId() : null,
                universityAdmin != null ? universityAdmin.getName() : null,
                departmentAdmin != null ? departmentAdmin.getId() : null,
                departmentAdmin != null ? departmentAdmin.getName() : null,
                majorAdmin != null ? majorAdmin.getId() : null,
                majorAdmin != null ? majorAdmin.getName() : null
        );
    }
}
