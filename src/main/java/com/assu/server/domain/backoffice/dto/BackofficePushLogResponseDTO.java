package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.backoffice.entity.BackofficePushLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record BackofficePushLogResponseDTO(

        @Schema(description = "로그 ID", example = "1")
        Long logId,

        @Schema(description = "발송 대상 유형", example = "ALL")
        String targetType,

        @Schema(description = "푸시 제목", example = "공지사항")
        String title,

        @Schema(description = "푸시 본문", example = "안녕하세요, 공지사항입니다.")
        String body,

        @Schema(description = "실제 발송된 수신자 수", example = "150")
        int recipientCount,

        @Schema(description = "발송 시각")
        LocalDateTime sentAt
) {
    public static BackofficePushLogResponseDTO from(BackofficePushLog log) {
        return new BackofficePushLogResponseDTO(
                log.getId(),
                log.getTargetType().name(),
                log.getTitle(),
                log.getBody(),
                log.getRecipientCount(),
                log.getSentAt()
        );
    }
}