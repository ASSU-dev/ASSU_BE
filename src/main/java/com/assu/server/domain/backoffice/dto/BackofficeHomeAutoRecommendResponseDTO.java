package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BackofficeHomeAutoRecommendResponseDTO(
    @Schema(description = "추천 기본 섹션 제목", example = "{name}님을 위한 제휴")
    String suggestedTitle,

    @Schema(description = "자동 추출된 상단 추천 업체")
    AutoFeaturedDTO featuredRecommendation,

    @Schema(description = "자동 추출된 추천 큐레이션 2개 그룹 (각 2개 업체)")
    List<AutoGroupDTO> curationLists
) {

    public record AutoFeaturedDTO(
        @Schema(description = "추천 업체 ID", example = "1")
        Long storeId,

        @Schema(description = "추천 업체 이름", example = "더진국 숭실대점")
        String storeName,

        @Schema(description = "추천 할인 내용", example = "전 메뉴 1,000원 할인 또는 음료수 증정")
        String discountContent,

        @Schema(description = "업체 카테고리", example = "RESTAURANT")
        String storeCategory
    ) {
        public static AutoFeaturedDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory
        ) {
            return new AutoFeaturedDTO(storeId, storeName, discountContent, storeCategory);
        }
    }

    public record AutoGroupDTO(
        @Schema(description = "그룹 인덱스 (1 또는 2)", example = "1")
        Integer groupIndex,

        @Schema(description = "그룹 소제목", example = "추천 제휴 1")
        String groupTitle,

        @Schema(description = "추천 업체 2개 목록")
        List<AutoStoreDTO> stores
    ) {
        public static AutoGroupDTO of(
            Integer groupIndex,
            String groupTitle,
            List<AutoStoreDTO> stores
        ) {
            return new AutoGroupDTO(groupIndex, groupTitle, stores);
        }
    }

    public record AutoStoreDTO(
        @Schema(description = "가게 ID", example = "2")
        Long storeId,

        @Schema(description = "가게 이름", example = "스타벅스 숭실대점")
        String storeName,

        @Schema(description = "자동 추출된 할인 내용", example = "아메리카노 사이즈업")
        String discountContent,

        @Schema(description = "가게 카테고리", example = "CAFE")
        String storeCategory,

        @Schema(description = "노출 순서", example = "1")
        Integer sortOrder
    ) {
        public static AutoStoreDTO of(
            Long storeId,
            String storeName,
            String discountContent,
            String storeCategory,
            Integer sortOrder
        ) {
            return new AutoStoreDTO(storeId, storeName, discountContent, storeCategory, sortOrder);
        }
    }

    public static BackofficeHomeAutoRecommendResponseDTO of(
        String suggestedTitle,
        AutoFeaturedDTO featuredRecommendation,
        List<AutoGroupDTO> curationLists
    ) {
        return new BackofficeHomeAutoRecommendResponseDTO(suggestedTitle, featuredRecommendation, curationLists);
    }
}
