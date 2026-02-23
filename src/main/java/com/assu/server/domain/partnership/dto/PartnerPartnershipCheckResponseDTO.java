package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PartnerPartnershipCheckResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        @NotNull Long paperId,

        @Schema(description = "제휴 여부", example = "true")
        boolean isPartnered,

        @Schema(description = "제휴 상태", example = "ACTIVE")
        @NotNull String status,

        @Schema(description = "관리자 ID", example = "101")
        @NotNull Long adminId,

        @Schema(description = "관리자 이름", example = "숭실대학교 총학생회")
        @NotNull String adminName,

        @Schema(description = "관리자 주소", example = "서울특별시 동작구 상도로")
        @NotNull String adminAddress
) {
    public static PartnerPartnershipCheckResponseDTO of(
            Admin admin,
            Long paperId,
            boolean isPartnered,
            String status
    ) {
        return new PartnerPartnershipCheckResponseDTO(
                paperId,
                isPartnered,
                status,
                admin.getId(),
                admin.getName(),
                admin.getOfficeAddress()
        );
    }
}
