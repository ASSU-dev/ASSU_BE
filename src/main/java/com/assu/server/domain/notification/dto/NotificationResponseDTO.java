package com.assu.server.domain.notification.dto;

import com.assu.server.domain.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
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
){
    public static NotificationResponseDTO from(Notification notification) {
        return new NotificationResponseDTO(
                notification.getId(),
                notification.getType().name(),
                notification.getRefId(),
                notification.getTitle(),
                notification.getMessagePreview(),
                notification.getDeeplink(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                toTimeAgo(notification.getCreatedAt())
        );
    }

    private static String toTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "";

        LocalDateTime now = LocalDateTime.now();
        if (createdAt.isAfter(now)) return "방금 전";

        long minutes = Duration.between(createdAt, now).toMinutes();
        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";

        long hours = minutes / 60;
        if (hours < 24) return hours + "시간 전";

        return hours / 24 + "일 전";
    }
}

