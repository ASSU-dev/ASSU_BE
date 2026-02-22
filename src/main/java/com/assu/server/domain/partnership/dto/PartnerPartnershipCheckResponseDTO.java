package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;

public record PartnerPartnershipCheckResponseDTO(
        @Schema(description = "제안서 ID", example = "1001")
        Long paperId,

        @Schema(description = "제휴 여부", example = "true")
        boolean isPartnered,

        @Schema(description = "제휴 상태", example = "ACTIVE")
        String status,

        @Schema(description = "관리자 ID", example = "101")
        Long adminId,

        @Schema(description = "관리자 이름", example = "숭실대학교 총학생회")
        String adminName,

        @Schema(description = "관리자 주소", example = "서울특별시 동작구 상도로")
        String adminAddress
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
