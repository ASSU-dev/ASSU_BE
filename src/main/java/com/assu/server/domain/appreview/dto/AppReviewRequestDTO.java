package com.assu.server.domain.appreview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "앱 리뷰 작성 요청")
public record AppReviewRequestDTO(
        @Schema(description = "별점 (1~5)", example = "5", minimum = "1", maximum = "5")
        @NotNull(message = "별점은 필수입니다.")
        @Min(value = 1, message = "별점은 1 이상이어야 합니다.")
        @Max(value = 5, message = "별점은 5 이하여야 합니다.")
        Integer rate,

        @Schema(description = "후기 내용", example = "너무 좋아용~")
        @NotBlank(message = "후기 내용은 비어 있을 수 없습니다.")
        String content
) {
}
