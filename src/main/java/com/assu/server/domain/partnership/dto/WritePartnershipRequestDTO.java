package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import jakarta.validation.Valid;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record WritePartnershipRequestDTO(
        @Schema(description = "수정할 제안서 ID", example = "1001")
        @NotNull Long paperId,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        @NotNull LocalDate partnershipPeriodStart,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        @NotNull LocalDate partnershipPeriodEnd,

        @Schema(description = "제휴 옵션 목록")
        @NotNull List<@Valid PartnershipOptionRequestDTO> options
) {
    public void updatePaper(Paper paper) {
        paper.changePartnershipPeriod(partnershipPeriodStart(), partnershipPeriodEnd());
        paper.changeStatus(ActivationStatus.SUSPEND);
    }

    public List<PaperContent> toPaperContents(Paper paper) {
        if (options() == null || options().isEmpty()) return Collections.emptyList();
        return options().stream()
                .map(opt -> opt.toPaperContent(paper))
                .toList();
    }
}
