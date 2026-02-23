package com.assu.server.domain.suggestion.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.user.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record WriteSuggestionRequestDTO(
        @Schema(description = "건의 대상 관리자 ID", example = "1")
        @NotNull Long adminId,

        @Schema(description = "희망 가게 이름", example = "스타벅스 숭실대점")
        @NotNull String storeName,

        @Schema(description = "희망 혜택", example = "아메리카노 10% 할인")
        @NotNull String benefit
) {
    public Suggestion toEntity(Admin admin, Student student) {
        return Suggestion.builder()
                .admin(admin)
                .student(student)
                .storeName(storeName())
                .content(benefit())
                .build();
    }
}
