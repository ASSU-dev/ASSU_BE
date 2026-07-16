package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.notification.entity.Notification;
import com.assu.server.domain.notification.entity.NotificationOutbox;
import io.swagger.v3.oas.annotations.media.Schema;

public record BackofficeOutboxResponseDTO(

        @Schema(description = "Outbox ID", example = "7")
        Long outboxId,

        @Schema(description = "알림 ID", example = "15")
        Long notificationId,

        @Schema(description = "수신자 멤버 ID", example = "42")
        Long receiverId,

        @Schema(description = "알림 타입", example = "STAMP")
        String type,

        @Schema(description = "알림 제목", example = "스탬프 10개 달성! 이벤트 응모 완료 🎁")
        String title,

        @Schema(description = "알림 미리보기 메시지", example = "스탬프 10개가 적립되어 기프티콘 증정 이벤트에 자동으로 응모되었어요.")
        String messagePreview,

        @Schema(description = "Outbox 상태 (PENDING / SENDING / DISPATCHED / SENT / FAILED)", example = "FAILED")
        String status,

        @Schema(description = "재시도 횟수", example = "3")
        int retryCount
) {
    public static BackofficeOutboxResponseDTO from(NotificationOutbox outbox) {
        Notification n = outbox.getNotification();
        return new BackofficeOutboxResponseDTO(
                outbox.getId(),
                n.getId(),
                n.getReceiver().getId(),
                n.getType().name(),
                n.getTitle(),
                n.getMessagePreview(),
                outbox.getStatus().name(),
                outbox.getRetryCount()
        );
    }
}