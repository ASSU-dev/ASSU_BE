package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Paper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record PartnershipStatusUpdateResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        @NotNull Long partnershipId,

        @Schema(description = "변경 전 제안서 상태", example = "SUSPEND")
        @NotNull String prevStatus,

        @Schema(description = "변경 후 제안서 상태", example = "ACTIVE")
        @NotNull String newStatus,

        @Schema(description = "상태 변경 시간", example = "2024-06-15T10:30:00")
        @NotNull LocalDateTime changedAt
) {
    public static PartnershipStatusUpdateResponseDTO of(
            Paper paper,
            ActivationStatus prevPaperStatus,
            ActivationStatus nextPaperStatus
    ) {
        return new PartnershipStatusUpdateResponseDTO(
                paper.getId(),
                prevPaperStatus == null ? null : prevPaperStatus.name(),
                nextPaperStatus.name(),
                LocalDateTime.now()
        );
    }
}
