package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Paper;

import java.time.LocalDateTime;

public record SuspendedPaperResponseDTO(
        Long paperId,
        String partnerName,
        LocalDateTime createdAt
) {
    public static SuspendedPaperResponseDTO of(Paper paper) {
        return new SuspendedPaperResponseDTO(
                paper.getId(),
                paper.getPartner() != null
                        ? paper.getPartner().getName()
                        : (paper.getStore() != null ? paper.getStore().getName() : "미등록"),
                paper.getCreatedAt()
        );
    }
}
