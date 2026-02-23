package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partnership.entity.Goods;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PartnershipGoodsResponseDTO(
        @Schema(description = "서비스 제공 항목 ID", example = "501")
        @NotNull Long goodsId,

        @Schema(description = "서비스 제공 항목명", example = "아메리카노")
        @NotNull String goodsName
) {
    public static PartnershipGoodsResponseDTO of(Goods goods) {
        return new PartnershipGoodsResponseDTO(goods.getId(), goods.getBelonging());
    }

    public static List<PartnershipGoodsResponseDTO> ofList(List<Goods> goods) {
        if (goods == null || goods.isEmpty()) return List.of();
        return goods.stream().map(PartnershipGoodsResponseDTO::of).toList();
    }
}
