package com.assu.server.domain.suggestion.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.suggestion.entity.Suggestion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백오피스 제휴 건의 응답")
public record BackofficeSuggestionResponseDTO(
        @Schema(description = "건의 ID", example = "12")
        Long suggestionId,

        @Schema(description = "작성 학생 ID", example = "3")
        Long studentId,

        @Schema(description = "작성 학생 이름", example = "홍길동")
        String studentName,

        @Schema(description = "건의 대상 학생회 ID", example = "10")
        Long adminId,

        @Schema(description = "건의 대상 학생회 이름", example = "숭실대학교 총학생회")
        String adminName,

        @Schema(description = "희망 제휴 가게명", example = "스타벅스 숭실대점")
        String storeName,

        @Schema(description = "건의 내용", example = "아메리카노 10% 할인 제휴 부탁드립니다.")
        String content,

        @Schema(description = "신고 상태 (NORMAL / REPORTED / DELETED)", example = "NORMAL")
        String reportedStatus,

        @Schema(description = "운영자 확인 여부", example = "false")
        boolean isChecked,

        @Schema(description = "운영자 확인 일시 (미확인이면 null)", example = "2026-11-21T15:00:00")
        LocalDateTime checkedAt,

        @Schema(description = "건의 작성 일시", example = "2026-11-21T14:30:00")
        LocalDateTime createdAt
) {
    public static BackofficeSuggestionResponseDTO from(Suggestion suggestion) {
        Student student = suggestion.getStudent();
        Admin admin = suggestion.getAdmin();
        return new BackofficeSuggestionResponseDTO(
                suggestion.getId(),
                student != null ? student.getId() : null,
                student != null ? student.getName() : null,
                admin != null ? admin.getId() : null,
                admin != null ? admin.getName() : null,
                suggestion.getStoreName(),
                suggestion.getContent(),
                suggestion.getStatus().name(),
                suggestion.getCheckedAt() != null,
                suggestion.getCheckedAt(),
                suggestion.getCreatedAt()
        );
    }
}
