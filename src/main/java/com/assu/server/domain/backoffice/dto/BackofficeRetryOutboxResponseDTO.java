package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record BackofficeRetryOutboxResponseDTO(

        @Schema(description = "재전송 큐에 등록된 Outbox ID", example = "7")
        Long outboxId
) {
    public static BackofficeRetryOutboxResponseDTO of(Long outboxId) {
        return new BackofficeRetryOutboxResponseDTO(outboxId);
    }
}
