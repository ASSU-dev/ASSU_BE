package com.assu.server.domain.suggestion.dto;

import com.assu.server.domain.suggestion.entity.Suggestion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record WriteSuggestionResponseDTO(
        @Schema(description = "건의 ID", example = "1")
        @NotNull Long suggestionId,

        @Schema(description = "제안인 ID", example = "10")
        @NotNull Long userId,

        @Schema(description = "건의 대상 관리자 ID", example = "1")
        @NotNull Long adminId,

        @Schema(description = "희망 가게 이름", example = "스타벅스 숭실대점")
        @NotNull String storeName,

        @Schema(description = "희망 혜택", example = "아메리카노 10% 할인")
        @NotNull String suggestionBenefit
) {
    public static WriteSuggestionResponseDTO of(Suggestion suggestion) {
        return new WriteSuggestionResponseDTO(
                suggestion.getId(),
                suggestion.getStudent().getId(),
                suggestion.getAdmin().getId(),
                suggestion.getStoreName(),
                suggestion.getContent()
        );
    }
}
