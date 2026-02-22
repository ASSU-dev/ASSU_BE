package com.assu.server.domain.partnership.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PartnershipGoodsRequestDTO(
        @Schema(description = "서비스 제공 항목명", example = "아메리카노")
        String goodsName
) {
}
