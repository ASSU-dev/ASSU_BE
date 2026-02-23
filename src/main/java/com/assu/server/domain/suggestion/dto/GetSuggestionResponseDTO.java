package com.assu.server.domain.suggestion.dto;

import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.user.entity.enums.EnrollmentStatus;
import com.assu.server.domain.user.entity.enums.Major;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record GetSuggestionResponseDTO(
        @Schema(description = "건의 ID", example = "1")
        @NotNull Long suggestionId,

        @Schema(description = "건의 작성일", example = "2026-01-04T12:00:00")
        @NotNull LocalDateTime createdAt,

        @Schema(description = "희망 가게 이름", example = "스타벅스 숭실대점")
        @NotNull String storeName,

        @Schema(description = "건의 내용", example = "아메리카노 10% 할인")
        @NotNull String content,

        @Schema(description = "건의자의 학부/학과", example = "COM")
        @NotNull Major studentMajor,

        @Schema(description = "재학 상태", example = "ENROLLED")
        @NotNull EnrollmentStatus enrollmentStatus
) {
    public static GetSuggestionResponseDTO of(Suggestion s) {
        return new GetSuggestionResponseDTO(
                s.getId(),
                s.getCreatedAt(),
                s.getStoreName(),
                s.getContent(),
                s.getStudent().getMajor(),
                s.getStudent().getEnrollmentStatus()
        );
    }
}
