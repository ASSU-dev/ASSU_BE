package com.assu.server.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record NotificationResponseDTO (
        @Schema(description = "알림 ID", example = "101")
        Long id,

        @Schema(description = "알림 유형", example = "INQUIRY_ANSWER")
        String type,

        @Schema(description = "관련 엔티티 참조 ID", example = "12")
        Long refId,

        @Schema(description = "알림 제목", example = "문의 답변이 등록되었습니다")
        String title,

        @Schema(description = "알림 미리보기 메시지", example = "문의하신 내용에 대한 답변이 등록되었습니다")
        String messagePreview,

        @Schema(description = "알림 클릭 시 이동하는 딥링크", example = "/inquiries/12")
        String deeplink,

        @Schema(description = "읽음 여부", example = "false")
        boolean isRead,

        @Schema(description = "알림 생성 일시", example = "2026-01-20T14:30:00")
        LocalDateTime createdAt,

        @Schema(description = "알림 읽은 일시", example = "2026-01-20T15:00:00")
        LocalDateTime readAt,

        @Schema(description = "현재 시각 기준 상대 시간 표현", example = "5분 전")
        String timeAgo
){}

