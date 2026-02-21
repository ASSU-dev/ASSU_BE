package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;

import java.util.List;

public record PartnershipOptionResponseDTO(
        OptionType optionType,
        CriterionType criterionType,
        Boolean anotherType,
        Integer people,
        Long cost,
        String note,
        String category,
        Long discountRate,
        List<PartnershipGoodsResponseDTO> goods
) {
    public static PartnershipOptionResponseDTO of(PaperContent pc, List<Goods> goods) {
        String note = pc.getNote() != null ? pc.getNote() : null;
        return new PartnershipOptionResponseDTO(
                pc.getOptionType(),
                pc.getCriterionType(),
                pc.getAnotherType(),
                pc.getPeople(),
                pc.getCost(),
                note,
                pc.getCategory(),
                pc.getDiscount(),
                PartnershipGoodsResponseDTO.ofList(goods)
        );
    }
}
