package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Paper;

public record PartnershipDraftResponseDTO(
        Long paperId
) {
    public static PartnershipDraftResponseDTO of(Paper paper) {
        return new PartnershipDraftResponseDTO(paper.getId());
    }
}
