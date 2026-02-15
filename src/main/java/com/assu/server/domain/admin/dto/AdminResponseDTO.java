package com.assu.server.domain.admin.dto;

import com.assu.server.domain.partner.entity.Partner;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminResponseDTO (
        @Schema(description = "제휴업체 ID", example = "101")
        Long partnerId,

        @Schema(description = "제휴업체 이름", example = "역전할머니 맥주 숭실대점")
        String partnerName,

        @Schema(description = "제휴업체 주소", example = "서울특별시 동작구")
        String partnerAddress,

        @Schema(description = "제휴업체 상세주소", example = "2층 201호")
        String partnerDetailAddress,

        @Schema(description = "제휴업체 URL", example = "https://www.beer.co.kr")
        String partnerUrl,

        @Schema(description = "제휴업체 전화번호", example = "02-123-4567")
        String partnerPhone
) {
    public static AdminResponseDTO from(Partner partner) {
        return new AdminResponseDTO(
                partner.getId(),
                partner.getName(),
                partner.getAddress(),
                partner.getDetailAddress(),
                partner.getMember() != null ? partner.getMember().getProfileUrl() : null,
                partner.getMember() != null ? partner.getMember().getPhoneNum() : null
        );
    }

}
