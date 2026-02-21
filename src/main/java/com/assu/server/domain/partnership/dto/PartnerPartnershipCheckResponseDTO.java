package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.admin.entity.Admin;

public record PartnerPartnershipCheckResponseDTO(
        Long paperId,
        boolean isPartnered,
        String status,
        Long adminId,
        String adminName,
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
