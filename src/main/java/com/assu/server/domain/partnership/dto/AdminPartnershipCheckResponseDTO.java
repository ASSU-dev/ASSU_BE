package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.partner.entity.Partner;

public record AdminPartnershipCheckResponseDTO(
        Long paperId,
        boolean isPartnered,
        String status,
        Long partnerId,
        String partnerName,
        String partnerAddress
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
