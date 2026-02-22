package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;

import java.util.ArrayList;
import java.util.List;

public record PartnershipOptionRequestDTO(
        OptionType optionType,
        CriterionType criterionType,
        Boolean anotherType,
        Integer people,
        Long cost,
        String category,
        Long discountRate,
        String note,
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
