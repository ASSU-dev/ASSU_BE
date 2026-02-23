package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Paper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SuspendedPaperResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        @NotNull Long paperId,

        @Schema(description = "제휴업체 이름", example = "역전할머니맥주 숭실대점")
        @NotNull String partnerName,

        @Schema(description = "제안서 생성 일자", example = "2024-01-01T09:00:00")
        @NotNull LocalDateTime createdAt
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
