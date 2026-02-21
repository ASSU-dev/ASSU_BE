package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Goods;

import java.util.List;

public record PartnershipGoodsResponseDTO(
        Long goodsId,
        String goodsName
) {
    public static PartnershipGoodsResponseDTO of(Goods goods) {
        return new PartnershipGoodsResponseDTO(goods.getId(), goods.getBelonging());
    }

    public static List<PartnershipGoodsResponseDTO> ofList(List<Goods> goods) {
        if (goods == null || goods.isEmpty()) return List.of();
        return goods.stream().map(PartnershipGoodsResponseDTO::of).toList();
    }
}
