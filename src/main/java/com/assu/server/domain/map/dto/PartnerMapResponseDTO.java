package com.assu.server.domain.map.dto;

import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.infra.s3.AmazonS3Manager;

import java.time.LocalDate;

public record PartnerMapResponseDTO(
        Long partnerId,
        String name,
        String address,
        boolean isPartnered,
        Long partnershipId,
        LocalDate partnershipStartDate,
        LocalDate partnershipEndDate,
        Double latitude,
        Double longitude,
        String profileUrl,
        String phoneNumber
) {
    public static PartnerMapResponseDTO of(Partner partner, Paper activePaper, AmazonS3Manager s3Manager) {
        final String key = partner.getMember() != null ? partner.getMember().getProfileUrl() : null;
        final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;

        assert partner.getMember() != null;
        return new PartnerMapResponseDTO(
                partner.getId(),
                partner.getName(),
                partner.getAddress() != null ? partner.getAddress() : partner.getDetailAddress(),
                activePaper != null,
                activePaper != null ? activePaper.getId() : null,
                activePaper != null ? activePaper.getPartnershipPeriodStart() : null,
                activePaper != null ? activePaper.getPartnershipPeriodEnd() : null,
                partner.getLatitude(),
                partner.getLongitude(),
                profileUrl,
                partner.getMember().getPhoneNum()
        );
    }
}
