package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "백오피스 제휴업체 프로필 상세")
public record BackofficePartnerProfileDetailDTO(
        @Schema(description = "업체명") String name,
        @Schema(description = "이메일") String email,
        @Schema(description = "전화번호") String phoneNum,
        @Schema(description = "주소") String address,
        @Schema(description = "상세 주소") String detailAddress,
        @Schema(description = "사업자등록증 검증 여부") Boolean isLicenseVerified,
        @Schema(description = "사업자등록증 검증 시각") LocalDateTime licenseVerifiedAt
) {
    public static BackofficePartnerProfileDetailDTO from(Member member) {
        Partner partner = member.getPartnerProfile();
        if (partner == null) {
            return null;
        }

        String email = member.getCommonAuth() != null ? member.getCommonAuth().getEmail() : null;

        return new BackofficePartnerProfileDetailDTO(
                partner.getName(),
                email,
                partner.getPhoneNum(),
                partner.getAddress(),
                partner.getDetailAddress(),
                partner.getIsLicenseVerified(),
                partner.getLicenseVerifiedAt()
        );
    }
}
