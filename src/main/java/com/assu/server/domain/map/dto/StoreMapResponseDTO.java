package com.assu.server.domain.map.dto;

import com.assu.server.domain.store.entity.Store;
import com.assu.server.infra.s3.AmazonS3Manager;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

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

        @Schema(description = "가게 프로필 Url", example = "https://www.beer.co.kr")
        String profileUrl,

        @Schema(description = "가게 전화번호", example = "010-1234-5678")
        String phoneNumber,

        @Schema(description = "관리자1 ID", example = "101")
        @NotNull Long adminId1,

        @Schema(description = "관리자2 ID", example = "102")
        Long adminId2,

        @Schema(description = "관리자1 이름", example = "숭실대학교 총학생회")
        @NotNull String adminName1,

        @Schema(description = "관리자2 이름", example = "숭실대학교 IT대학 학생회")
        String adminName2,

        @Schema(description = "제휴 혜택1", example = "음료 10% 할인")
        @NotNull String benefit1,

        @Schema(description = "제휴 혜택2", example = "버터구이 오징어 제공")
        String benefit2
) {
    public static StoreMapResponseDTO of(
            Store store,
            Long adminId1, Long adminId2,
            String adminName1, String adminName2,
            String benefit1, String benefit2,
            AmazonS3Manager s3Manager
    ) {
        final boolean hasPartner = store.getPartner() != null;
        final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
                ? store.getPartner().getMember().getProfileUrl() : null;
        final String profileUrl = (key != null && !key.isBlank())
                ? s3Manager.generatePresignedUrl(key) : null;
        final String phoneNumber = (store.getPartner() != null
                && store.getPartner().getMember() != null
                && store.getPartner().getMember().getPhoneNum() != null)
                ? store.getPartner().getMember().getPhoneNum() : "";

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
                adminId1, adminId2,
                adminName1, adminName2,
                benefit1, benefit2
        );
    }
}
