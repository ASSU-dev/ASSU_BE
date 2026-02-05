package com.assu.server.domain.inquiry.dto;
import com.assu.server.domain.inquiry.entity.Inquiry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record InquiryResponseDTO(
        @Schema(description = "문의 ID", example = "12")
        Long id,

        @Schema(description = "문의 제목", example = "상호명 변경 요청드립니다.")
        String title,

        @Schema(description = "문의 내용", example = "안녕하세요. 역할맥입니다. 상호명을 변경하고 싶습니다.")
        String content,

        @Schema(description = "문의자 이메일", example = "assu@gmail.com")
        String email,

        @Schema(description = "문의 상태", example = "WAITING, ANSWERED, ALL")
        String status,

        @Schema(description = "답변 내용", example = "안녕하세요. A:SSU 팀입니다.")
        String answer,

        @Schema(description = "문의 생성 일시", example = "2026-01-20T14:30:00")
        LocalDateTime createdAt
) {
    public static InquiryResponseDTO from(Inquiry inquiry) {
        return new InquiryResponseDTO(
                inquiry.getId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getEmail(),
                inquiry.getStatus().name(),
                inquiry.getAnswer(),
                inquiry.getCreatedAt()
        );
    }
}