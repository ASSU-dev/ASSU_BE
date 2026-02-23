package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public record PartnershipOptionRequestDTO(
        @Schema(description = "제공 서비스 종류 (SERVICE: 서비스 제공, DISCOUNT: 할인)", example = "SERVICE")
        @NotNull OptionType optionType,

        @Schema(description = "서비스 제공 기준 (PRICE: 금액, HEADCOUNT: 인원)", example = "HEADCOUNT")
        @NotNull CriterionType criterionType,

        @Schema(description = "기타 제공 서비스 여부", example = "false")
        @NotNull Boolean anotherType,

        @Schema(description = "서비스 제공 기준 인원 수", example = "2")
        Integer people,

        @Schema(description = "서비스 제공 기준 금액", example = "10000")
        Long cost,

        @Schema(description = "서비스 카테고리 (서비스 제공 항목이 여러 개일 때 작성)", example = "음료")
        String category,

        @Schema(description = "할인율", example = "10")
        Long discountRate,

        @Schema(description = "기타 유형 제휴 옵션 문구", example = "웰컴 드링크 제공")
        String note,

        @Schema(description = "서비스 제공 항목 목록")
        List<PartnershipGoodsRequestDTO> goods
) {
    public PaperContent toPaperContent(Paper paper) {
        return PaperContent.builder()
                .note(note())
                .paper(paper)
                .optionType(optionType())
                .criterionType(criterionType())
                .anotherType(anotherType())
                .people(people())
                .cost(cost())
                .category(category())
                .discount(discountRate())
                .build();
    }

    public List<Goods> toGoods(PaperContent content) {
        if (goods() == null || goods().isEmpty()) return List.of();
        List<Goods> batch = new ArrayList<>(goods().size());
        for (var g : goods()) {
            batch.add(Goods.builder()
                    .content(content)
                    .belonging(g.goodsName())
                    .build());
        }
        return batch;
    }
}
