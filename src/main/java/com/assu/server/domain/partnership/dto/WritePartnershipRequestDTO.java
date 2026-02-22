package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record WritePartnershipRequestDTO(
        @Schema(description = "수정할 제안서 ID", example = "1001")
        Long paperId,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        LocalDate partnershipPeriodStart,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        LocalDate partnershipPeriodEnd,

        @Schema(description = "제휴 옵션 목록")
        List<PartnershipOptionRequestDTO> options
) {
    public void updatePaper(Paper paper) {
        paper.setPartnershipPeriodStart(partnershipPeriodStart());
        paper.setPartnershipPeriodEnd(partnershipPeriodEnd());
        paper.setIsActivated(ActivationStatus.SUSPEND);
    }

    public List<PaperContent> toPaperContents(Paper paper) {
        if (options() == null || options().isEmpty()) return Collections.emptyList();
        return options().stream()
                .map(opt -> opt.toPaperContent(paper))
                .toList();
    }

    public List<List<Goods>> toGoodsBatches() {
        if (options() == null || options().isEmpty()) return Collections.emptyList();
        return options().stream()
                .map(optionDto -> {
                    if (optionDto.goods() == null || optionDto.goods().isEmpty()) {
                        return Collections.<Goods>emptyList();
                    }
                    return optionDto.goods().stream()
                            .map(goodsDto -> Goods.builder()
                                    .belonging(goodsDto.goodsName())
                                    .build())
                            .toList();
                })
                .toList();
    }
}
