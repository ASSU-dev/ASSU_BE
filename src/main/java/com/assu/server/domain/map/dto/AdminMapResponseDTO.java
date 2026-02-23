package com.assu.server.domain.map.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.infra.s3.AmazonS3Manager;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AdminMapResponseDTO(
        @Schema(description = "관리자 ID", example = "101")
        @NotNull Long adminId,

        @Schema(description = "관리자 이름", example = "숭실대학교 총학생회")
        @NotNull String name,

        @Schema(description = "관리자 주소", example = "서울특별시 동작구 상도로")
        @NotNull String address,

        @Schema(description = "제휴업체와 제휴여부", example = "true")
        @NotNull boolean isPartnered,

        @Schema(description = "제휴 ID", example = "101")
        Long partnershipId,

        @Schema(description = "제휴 시작일", example = "2024-01-01")
        LocalDate partnershipStartDate,

        @Schema(description = "제휴 마감일", example = "2024-12-31")
        LocalDate partnershipEndDate,

        @Schema(description = "관리자 위도", example = "57.56")
        @NotNull Double latitude,

        @Schema(description = "관리자 경도", example = "37.38")
        @NotNull Double longitude,

        @Schema(description = "관리자 카카오맵 Url", example = "https://www.beer.co.kr")
        String profileUrl,

        @Schema(description = "관리자 전화번호", example = "010-1234-5678")
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
