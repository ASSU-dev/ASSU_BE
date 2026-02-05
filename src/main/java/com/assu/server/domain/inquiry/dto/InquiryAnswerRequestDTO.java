package com.assu.server.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record InquiryAnswerRequestDTO (
        @NotBlank(message = "answer는 비어 있을 수 없습니다.")
        @Schema(description = "답변 내용", example = "안녕하세요. A:SSU 팀입니다.")
        String answer
) {}