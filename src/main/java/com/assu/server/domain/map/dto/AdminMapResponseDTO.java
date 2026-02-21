package com.assu.server.domain.map.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.infra.s3.AmazonS3Manager;

import java.time.LocalDate;

public record AdminMapResponseDTO(
        Long adminId,
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
    public static AdminMapResponseDTO of(Admin admin, Paper activePaper, AmazonS3Manager s3Manager) {
        final String key = admin.getMember() != null ? admin.getMember().getProfileUrl() : null;
        final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;

        assert admin.getMember() != null;
        return new AdminMapResponseDTO(
                admin.getId(),
                admin.getName(),
                admin.getOfficeAddress() != null ? admin.getOfficeAddress() : admin.getDetailAddress(),
                activePaper != null,
                activePaper != null ? activePaper.getId() : null,
                activePaper != null ? activePaper.getPartnershipPeriodStart() : null,
                activePaper != null ? activePaper.getPartnershipPeriodEnd() : null,
                admin.getLatitude(),
                admin.getLongitude(),
                profileUrl,
                admin.getMember().getPhoneNum()
        );
    }
}
