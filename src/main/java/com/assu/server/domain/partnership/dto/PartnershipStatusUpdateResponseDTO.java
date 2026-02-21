package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Paper;

import java.time.LocalDateTime;

public record PartnershipStatusUpdateResponseDTO(
        Long partnershipId,
        String prevStatus,
        String newStatus,
        LocalDateTime changedAt
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
