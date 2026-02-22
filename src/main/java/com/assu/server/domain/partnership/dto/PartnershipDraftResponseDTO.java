package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Paper;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartnershipDraftResponseDTO(
        @Schema(description = "생성된 제안서 ID", example = "1001")
        Long paperId
) {
    public static PartnershipDraftResponseDTO of(Paper paper) {
        return new PartnershipDraftResponseDTO(paper.getId());
    }
}
