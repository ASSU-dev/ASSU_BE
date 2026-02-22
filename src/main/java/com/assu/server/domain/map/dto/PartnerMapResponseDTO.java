package com.assu.server.domain.map.dto;

import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.infra.s3.AmazonS3Manager;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PartnerMapResponseDTO(
        @Schema(description = "파트너 ID", example = "101")
        @NotNull Long partnerId,

        @Schema(description = "제휴업체 이름", example = "역전할머니맥주 숭실대점")
        @NotNull String name,

        @Schema(description = "제휴업체 주소", example = "서울특별시 동작구 상도로")
        @NotNull String address,

        @Schema(description = "제휴업체와 제휴여부", example = "true")
        @NotNull boolean isPartnered,

        @Schema(description = "제휴 ID", example = "101")
        Long partnershipId,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        LocalDate partnershipStartDate,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        LocalDate partnershipEndDate,

        @Schema(description = "제휴업체 위도", example = "37.50")
        @NotNull Double latitude,

        @Schema(description = "제휴업체 경도", example = "126.96")
        @NotNull Double longitude,

        @Schema(description = "제휴업체 프로필 Url", example = "https://www.beer.co.kr")
        String profileUrl,

        @Schema(description = "제휴업체 전화번호", example = "010-1234-5678")
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
