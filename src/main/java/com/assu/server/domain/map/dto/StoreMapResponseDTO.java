package com.assu.server.domain.map.dto;

import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.entity.enums.LinkType;
import com.assu.server.domain.store.entity.enums.StoreCategory;
import com.assu.server.infra.s3.ImageCdnManager;
import com.assu.server.infra.s3.ImageSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StoreMapResponseDTO(
        @Schema(description = "가게 ID", example = "201")
        @NotNull Long storeId,

        @Schema(description = "가게 이름", example = "숭실마트")
        @NotNull String name,

        @Schema(description = "가게 주소", example = "서울특별시 동작구 상도로")
        @NotNull String address,

        @Schema(description = "가게 평점", example = "4")
        Integer rate,

        @Schema(description = "제휴업체인지 여부 (관련 Paper가 없으면 false)", example = "true")
        @NotNull boolean hasPartner,

        @Schema(description = "가게 위도", example = "37.50")
        @NotNull Double latitude,

        @Schema(description = "가게 경도", example = "126.96")
        @NotNull Double longitude,

        @Schema(description = "가게 프로필 이미지 URL (256px WebP, 서명된 CDN URL)", example = "https://img.assu.site/fit-in/256x256/filters:format(webp):quality(80)/public/store-profiles/3f2a9c1e.png?signature=9b1c...")
        String profileUrl,

        @Schema(description = "가게 전화번호", example = "010-1234-5678")
        String phoneNumber,

        @Schema(description = "모든 제휴 혜택 목록")
        List<PartnershipInfo> partnerships,

        @Schema(description = "가게 카테고리", example = "CAFE")
        StoreCategory storeCategory,

        @Schema(description = "외부 링크로 리다이렉트되어야 하는 가게인지 구분하는 타입 (해당 없으면 null)", example = "EXTERNAL")
        LinkType linkType
) {
    public record PartnershipInfo(
            Long adminId,
            String adminName,
            List<String> benefits
    ) {}

    public static StoreMapResponseDTO ofWithPartnerships(
            Store store,
            List<PartnershipInfo> partnerships,
            ImageCdnManager imageCdnManager
    ) {
        final boolean hasPartner = store.getPartner() != null;
        final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
                ? store.getPartner().getMember().getProfileUrl() : null;
        final String profileUrl = imageCdnManager.generateUrl(key, ImageSize.THUMBNAIL);
        final String phoneNumber = (store.getPartner() != null
                && store.getPartner().getPhoneNum() != null)
                ? store.getPartner().getPhoneNum() : "";

        return new StoreMapResponseDTO(
                store.getId(),
                store.getName(),
                store.getAddress() != null ? store.getAddress() : store.getDetailAddress(),
                store.getRate(),
                hasPartner,
                store.getLatitude(),
                store.getLongitude(),
                profileUrl,
                phoneNumber,
                partnerships,
                store.getStoreCategory(),
                store.getLinkType()
        );
    }
}
