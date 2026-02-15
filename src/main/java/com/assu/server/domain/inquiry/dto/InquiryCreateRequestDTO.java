package com.assu.server.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record InquiryCreateRequestDTO (
        @NotBlank(message = "title은 비어 있을 수 없습니다.")
        @Schema(description = "문의 제목", example = "상호명 변경 요청드립니다.")
        String title,

        @NotBlank(message = "content는 비어 있을 수 없습니다.")
        @Schema(description = "문의 내용", example = "안녕하세요. 역할맥입니다. 상호명을 변경하고 싶습니다.")
        String content,

        @NotBlank(message = "email은 비어 있을 수 없습니다.")
        @Schema(description = "문의자 이메일", example = "assu@gmail.com")
        String email
){ }
