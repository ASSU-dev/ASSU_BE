package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partner.entity.Partner;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;

public record AdminPartnershipCheckResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        @NotNull Long paperId,

        @Schema(description = "제휴 여부", example = "true")
        boolean isPartnered,

        @Schema(description = "제휴 상태", example = "ACTIVE")
        @NotNull String status,

        @Schema(description = "제휴업체 ID", example = "201")
        @NotNull Long partnerId,

        @Schema(description = "제휴업체 이름", example = "역전할머니맥주 숭실대점")
        @NotNull String partnerName,

        @Schema(description = "제휴업체 주소", example = "서울특별시 동작구 상도로")
        @NotNull String partnerAddress
) {
    public static AdminPartnershipCheckResponseDTO of(
            Partner partner,
            Long paperId,
            boolean isPartnered,
            String status
    ) {
        return new AdminPartnershipCheckResponseDTO(
                paperId,
                isPartnered,
                status,
                partner.getId(),
                partner.getName(),
                partner.getAddress()
        );
    }
}
